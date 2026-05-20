package com.profitsaathi.seller.aichat;

import com.profitsaathi.auth.AuthenticatedPrincipal;
import com.profitsaathi.seller.aichat.AiChatDtos.GeneratedImageResponse;
import com.profitsaathi.seller.aichat.AiChatDtos.MessageView;
import com.profitsaathi.seller.aichat.AiChatDtos.ProviderStatus;
import com.profitsaathi.seller.aichat.AiChatDtos.SendRequest;
import com.profitsaathi.seller.aichat.AiChatDtos.SendResponse;
import com.profitsaathi.seller.aichat.AiChatDtos.SessionDetail;
import com.profitsaathi.seller.aichat.AiChatDtos.SessionSummary;
import com.profitsaathi.seller.aichat.imagegen.ImageGenRouter;
import com.profitsaathi.seller.aichat.imagegen.SaathiImageStorage;
import com.profitsaathi.seller.user.Seller;
import com.profitsaathi.seller.user.SellerRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Saathi AI — single orchestration entry point for the seller-facing chat
 * assistant. All routes from {@link AiChatController} flow through here.
 *
 * Responsibilities:
 * <ul>
 *   <li>Chat send: history fetch → provider call → persist user+assistant turns.</li>
 *   <li>Image gen/edit: provider call → save bytes to disk → persist a message row
 *       so the image survives session reloads.</li>
 *   <li>Per-seller provider keys (Gemini / OpenRouter / NVIDIA) and the
 *       primary-provider selector.</li>
 * </ul>
 *
 * <p>Named {@code SaathiAiService} (not {@code AiChatService}) to avoid the
 * bean-name collision with the monolith's older
 * {@code com.profitsaathi.ai.AiChatService} which serves the cached LLM gateway.
 *
 * <p><b>Daily limits intentionally absent</b>: per the product spec, sellers
 * bring their own API keys, so the provider itself rate-limits — we don't need
 * to double-gate inside the app. If you ever want to gate by subscription
 * tier later, add a {@code @PreAuthorize} on the controller, not a counter
 * here.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SaathiAiService {

    /** Soft cap on uploaded image size — keeps a runaway client from posting a 10 MB blob. */
    private static final int MAX_IMAGE_BYTES = 4 * 1024 * 1024;

    private final SellerRepository sellerRepository;
    private final AiChatSessionRepository sessionRepository;
    private final AiChatMessageRepository messageRepository;
    private final AiChatProviderRouter chatRouter;
    private final ImageGenRouter imageRouter;
    private final SaathiImageStorage imageStorage;
    private final com.profitsaathi.seller.aichat.imagegen.ImageCompressor imageCompressor;

    // ─────────────────────────────────────────────────────────────────────
    // Chat send
    // ─────────────────────────────────────────────────────────────────────

    /** Max number of prior user-uploaded images to replay in history per call. */
    private static final int MAX_HISTORY_IMAGES = 5;

    @Transactional
    public SendResponse send(AuthenticatedPrincipal me, SendRequest req) {
        boolean hasImage = req.imageBase64() != null && !req.imageBase64().isBlank();
        boolean hasText  = req.message()     != null && !req.message().isBlank();
        if (!hasText && !hasImage) {
            throw new IllegalArgumentException("Send a message or attach an image.");
        }
        if (hasImage && req.imageBase64().length() > (MAX_IMAGE_BYTES * 4 / 3)) {
            throw new IllegalArgumentException("Image is too large — please upload one under 4 MB.");
        }

        Seller seller = sellerRepository.findById(me.subjectId())
                .orElseThrow(() -> new EntityNotFoundException("Seller not found"));

        AiChatSession session = req.sessionId() != null
                ? sessionRepository.findByIdAndSellerId(req.sessionId(), me.subjectId())
                        .orElseThrow(() -> new EntityNotFoundException("Chat session not found"))
                : openNewSession(me.subjectId(), req.message());

        // ── History with images: load prior messages and re-attach any image
        //    bytes the seller uploaded earlier. Without this, the model loses
        //    the product photo after the first turn and gives generic answers
        //    on every follow-up — the "chat memory" bug. We cap at the last
        //    MAX_HISTORY_IMAGES images so a 20-turn session doesn't ship 80 MB.
        List<ChatTurn> history = buildHistoryWithImages(session.getId());

        ChatTurn currentTurn = hasImage
                ? ChatTurn.userWithImage(req.message(), req.imageMime(), req.imageBase64())
                : ChatTurn.user(req.message());

        long startedAt = System.currentTimeMillis();
        AiChatProviderRouter.Result result = chatRouter.chat(seller, history, currentTurn);
        long dur = System.currentTimeMillis() - startedAt;
        // INFO-level success log so ops can grep "[saathi-chat]" to see which
        // provider is actually serving traffic. Format intentionally compact
        // so it's pleasant in the running log.
        log.info("[saathi-chat] ok seller={} session={} provider={} hasImage={} dur={}ms",
                me.subjectId(), session.getId(), result.providerUsed(), hasImage, dur);

        // Persist USER turn. If it carries an image, save the bytes to disk
        // so future turns in this session can replay them (see
        // buildHistoryWithImages above). We persist user-uploaded images for
        // the same reason we persist assistant-generated ones — to survive
        // session reloads and give the model durable visual context.
        AiChatMessage userMsg = new AiChatMessage();
        userMsg.setSessionId(session.getId());
        userMsg.setRole("USER");
        userMsg.setContent(req.message() == null ? "" : req.message());
        userMsg.setHasImage(hasImage);
        if (hasImage) {
            userMsg.setImageMime(req.imageMime());
        }
        userMsg = messageRepository.save(userMsg);
        if (hasImage) {
            try {
                byte[] bytes = java.util.Base64.getDecoder().decode(req.imageBase64());
                String relPath = imageStorage.save(seller.getId(), userMsg.getId(),
                        new com.profitsaathi.seller.aichat.imagegen.ImageGenResult(
                                req.imageMime() == null ? "image/jpeg" : req.imageMime(), bytes));
                userMsg.setImagePath(relPath);
                messageRepository.save(userMsg);
            } catch (IOException | IllegalArgumentException e) {
                // Disk error or malformed base64 — don't fail the whole call,
                // the model already answered using the image we passed inline.
                // Memory for THIS session will be partial, that's acceptable.
                log.warn("Could not persist uploaded image for memory replay: {}", e.getMessage());
            }
        }

        AiChatMessage botMsg = new AiChatMessage();
        botMsg.setSessionId(session.getId());
        botMsg.setRole("ASSISTANT");
        botMsg.setContent(result.reply());
        botMsg.setHasImage(false);
        botMsg.setChatProvider(result.providerUsed().name());
        messageRepository.save(botMsg);

        session.setUpdatedAt(LocalDateTime.now());
        sessionRepository.save(session);

        return new SendResponse(session.getId(), result.reply(), result.providerUsed().name());
    }

    /**
     * Builds the {@link ChatTurn} history for this session. User messages
     * that originally carried an uploaded photo get the bytes re-attached
     * from disk so multimodal models keep "remembering" the product. Image
     * bytes are only attached to the most recent {@link #MAX_HISTORY_IMAGES}
     * to keep request size in check.
     *
     * <p>Images are compressed on-the-fly (resize to 1024×1024, JPEG 85%)
     * to reduce memory usage from ~4MB per image to ~100KB per image.
     * This allows keeping more images in history without hitting API limits.
     */
    private List<ChatTurn> buildHistoryWithImages(Long sessionId) {
        List<AiChatMessage> all = messageRepository.findBySessionIdOrderByIdAsc(sessionId);

        // Find the cutoff after which we re-attach images. Iterate backwards
        // through the user messages and mark the last MAX_HISTORY_IMAGES
        // image-bearing ones as "should replay".
        java.util.Set<Long> replay = new java.util.HashSet<>();
        int kept = 0;
        for (int i = all.size() - 1; i >= 0 && kept < MAX_HISTORY_IMAGES; i--) {
            AiChatMessage m = all.get(i);
            if ("USER".equals(m.getRole()) && m.getImagePath() != null && !m.getImagePath().isBlank()) {
                replay.add(m.getId());
                kept++;
            }
        }

        List<ChatTurn> history = new ArrayList<>(all.size());
        for (AiChatMessage m : all) {
            String role = "USER".equals(m.getRole()) ? "user" : "assistant";
            if (replay.contains(m.getId())) {
                try {
                    byte[] bytes = imageStorage.loadBytes(m.getImagePath());
                    
                    // Compress image for history to reduce memory usage
                    // (4MB → ~100KB per image)
                    if (imageCompressor.shouldCompress(bytes)) {
                        try {
                            com.profitsaathi.seller.aichat.imagegen.ImageGenResult compressed =
                                    imageCompressor.compress(bytes, m.getImageMime());
                            bytes = compressed.bytes();
                            // Update MIME to JPEG (compression always outputs JPEG)
                            String b64 = java.util.Base64.getEncoder().encodeToString(bytes);
                            history.add(new ChatTurn(role, m.getContent(), compressed.mimeType(), b64));
                            continue;
                        } catch (IOException e) {
                            log.warn("Image compression failed for {} — using original: {}",
                                    m.getImagePath(), e.getMessage());
                            // Fall through to use original bytes
                        }
                    }
                    
                    // Use original bytes (either small enough or compression failed)
                    String b64 = java.util.Base64.getEncoder().encodeToString(bytes);
                    history.add(new ChatTurn(role, m.getContent(), m.getImageMime(), b64));
                    continue;
                } catch (IOException e) {
                    log.warn("Could not load history image {} — falling back to text-only turn: {}",
                            m.getImagePath(), e.getMessage());
                }
            }
            history.add(new ChatTurn(role, m.getContent(), null, null));
        }
        return history;
    }

    // ─────────────────────────────────────────────────────────────────────
    // Image generation / edit
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Text-to-image when {@code inputBase64} is null/blank; image-to-image
     * edit otherwise. Persists the result bytes to disk and saves a chat
     * message so the image survives session reloads. Fetch back via
     * {@code GET /api/v1/ai/saathi/image/{messageId}}.
     */
    @Transactional
    public GeneratedImageResponse generateImage(AuthenticatedPrincipal me, Long sessionId, String prompt,
                                                String inputMime, String inputBase64) {
        if (prompt == null || prompt.isBlank()) {
            throw new IllegalArgumentException("Image prompt is required.");
        }
        if (prompt.length() > 1000) {
            throw new IllegalArgumentException("Image prompt is too long — keep it under 1000 characters.");
        }
        boolean isEdit = inputBase64 != null && !inputBase64.isBlank();
        if (isEdit && inputBase64.length() > (MAX_IMAGE_BYTES * 4 / 3)) {
            throw new IllegalArgumentException("Source image is too large — please upload one under 4 MB.");
        }

        Seller seller = sellerRepository.findById(me.subjectId())
                .orElseThrow(() -> new EntityNotFoundException("Seller not found"));

        AiChatSession session = sessionId != null
                ? sessionRepository.findByIdAndSellerId(sessionId, me.subjectId())
                        .orElseThrow(() -> new EntityNotFoundException("Chat session not found"))
                : openNewSession(me.subjectId(), (isEdit ? "✏️ " : "🎨 ") + prompt);

        // USER prompt as a turn so the seller can scroll back and see what
        // they asked for. hasImage=true on edit so the bubble shows an image
        // marker even though we don't store the source bytes.
        AiChatMessage userMsg = new AiChatMessage();
        userMsg.setSessionId(session.getId());
        userMsg.setRole("USER");
        userMsg.setContent(prompt);
        userMsg.setHasImage(isEdit);
        messageRepository.save(userMsg);

        long imgStarted = System.currentTimeMillis();
        ImageGenRouter.Result result = isEdit
                ? imageRouter.editImage(seller, prompt, inputMime, inputBase64)
                : imageRouter.generate(seller, prompt);
        log.info("[saathi-image] ok seller={} session={} mode={} provider={} mime={} dur={}ms",
                me.subjectId(), session.getId(), isEdit ? "edit" : "gen",
                result.providerUsed(), result.image().mimeType(),
                System.currentTimeMillis() - imgStarted);

        AiChatMessage botMsg = new AiChatMessage();
        botMsg.setSessionId(session.getId());
        botMsg.setRole("ASSISTANT");
        botMsg.setContent(prompt);
        botMsg.setHasImage(true);
        botMsg.setImageMime(result.image().mimeType());
        botMsg.setImageProvider(result.providerUsed());
        botMsg = messageRepository.save(botMsg);

        try {
            String relPath = imageStorage.save(seller.getId(), botMsg.getId(), result.image());
            botMsg.setImagePath(relPath);
            messageRepository.save(botMsg);
        } catch (IOException e) {
            log.error("Failed to persist Saathi-generated image", e);
            throw new IllegalStateException("Could not save the generated image: " + e.getMessage());
        }

        session.setUpdatedAt(LocalDateTime.now());
        sessionRepository.save(session);

        return new GeneratedImageResponse(
                session.getId(),
                botMsg.getId(),
                result.providerUsed(),
                result.image().mimeType());
    }

    /** Bytes of a previously-generated image, scoped to the calling seller. */
    @Transactional(readOnly = true)
    public StoredImage loadImage(AuthenticatedPrincipal me, Long messageId) {
        AiChatMessage msg = messageRepository.findById(messageId)
                .orElseThrow(() -> new EntityNotFoundException("Image not found"));

        AiChatSession session = sessionRepository.findByIdAndSellerId(msg.getSessionId(), me.subjectId())
                .orElseThrow(() -> new EntityNotFoundException("Image not found"));
        if (msg.getImagePath() == null || msg.getImagePath().isBlank()
                || !imageStorage.exists(msg.getImagePath())) {
            throw new EntityNotFoundException("Image not found");
        }
        return new StoredImage(msg.getImagePath(), msg.getImageMime(), session.getId());
    }

    public record StoredImage(String relativePath, String mimeType, Long sessionId) {}

    public java.io.InputStream openImageStream(StoredImage img) throws IOException {
        return imageStorage.open(img.relativePath());
    }

    // ─────────────────────────────────────────────────────────────────────
    // Session browsing
    // ─────────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<SessionSummary> listSessions(AuthenticatedPrincipal me) {
        return sessionRepository.findBySellerIdOrderByUpdatedAtDesc(me.subjectId()).stream()
                .map(s -> new SessionSummary(s.getId(), s.getTitle(), s.getCreatedAt(), s.getUpdatedAt()))
                .toList();
    }

    @Transactional(readOnly = true)
    public SessionDetail getSession(AuthenticatedPrincipal me, Long sessionId) {
        AiChatSession s = sessionRepository.findByIdAndSellerId(sessionId, me.subjectId())
                .orElseThrow(() -> new EntityNotFoundException("Chat session not found"));
        List<MessageView> messages = messageRepository.findBySessionIdOrderByIdAsc(sessionId).stream()
                .map(m -> new MessageView(
                        m.getId(),
                        m.getRole(),
                        m.getContent(),
                        m.isHasImage(),
                        m.getImagePath(),
                        m.getImageMime(),
                        m.getImageProvider(),
                        m.getChatProvider(),
                        m.getCreatedAt()))
                .toList();
        return new SessionDetail(
                new SessionSummary(s.getId(), s.getTitle(), s.getCreatedAt(), s.getUpdatedAt()),
                messages);
    }

    @Transactional
    public void deleteSession(AuthenticatedPrincipal me, Long sessionId) {
        AiChatSession s = sessionRepository.findByIdAndSellerId(sessionId, me.subjectId())
                .orElseThrow(() -> new EntityNotFoundException("Chat session not found"));
        messageRepository.findBySessionIdOrderByIdAsc(sessionId).forEach(messageRepository::delete);
        sessionRepository.delete(s);
    }

    // ─────────────────────────────────────────────────────────────────────
    // Provider keys (per-seller, AES-encrypted at rest on the sellers row)
    // ─────────────────────────────────────────────────────────────────────

    @Transactional
    public ProviderStatus saveApiKey(AuthenticatedPrincipal me, AiProvider provider, String apiKey) {
        Seller seller = sellerRepository.findById(me.subjectId())
                .orElseThrow(() -> new EntityNotFoundException("Seller not found"));
        String normalized = apiKey == null ? null : apiKey.trim();
        if (normalized != null && normalized.isEmpty()) normalized = null;
        switch (provider) {
            case GEMINI     -> seller.setGeminiApiKey(normalized);
            case OPENROUTER -> seller.setOpenrouterApiKey(normalized);
            case NVIDIA     -> seller.setNvidiaApiKey(normalized);
        }
        // First-time save: auto-pin this provider as primary so the chat
        // immediately works without an extra click on the selector.
        if (normalized != null && (seller.getAiPrimaryProvider() == null
                || AiProvider.fromString(seller.getAiPrimaryProvider()) == null)) {
            seller.setAiPrimaryProvider(provider.name());
        }
        sellerRepository.save(seller);
        return buildProviderStatus(seller);
    }

    @Transactional
    public ProviderStatus setPrimaryProvider(AuthenticatedPrincipal me, AiProvider provider) {
        Seller seller = sellerRepository.findById(me.subjectId())
                .orElseThrow(() -> new EntityNotFoundException("Seller not found"));
        seller.setAiPrimaryProvider(provider.name());
        sellerRepository.save(seller);
        return buildProviderStatus(seller);
    }

    @Transactional(readOnly = true)
    public ProviderStatus providerStatus(AuthenticatedPrincipal me) {
        Seller seller = sellerRepository.findById(me.subjectId())
                .orElseThrow(() -> new EntityNotFoundException("Seller not found"));
        return buildProviderStatus(seller);
    }

    // ─────────────────────────────────────────────────────────────────────
    // Internals
    // ─────────────────────────────────────────────────────────────────────

    private static ProviderStatus buildProviderStatus(Seller s) {
        List<String> configured = new ArrayList<>(3);
        if (s.isGeminiApiKeySet())     configured.add(AiProvider.GEMINI.name());
        if (s.isOpenrouterApiKeySet()) configured.add(AiProvider.OPENROUTER.name());
        if (s.isNvidiaApiKeySet())     configured.add(AiProvider.NVIDIA.name());
        AiProvider primary = AiProvider.fromString(s.getAiPrimaryProvider());
        return new ProviderStatus(
                s.isGeminiApiKeySet(),
                s.isOpenrouterApiKeySet(),
                s.isNvidiaApiKeySet(),
                primary == null ? null : primary.name(),
                configured);
    }

    private AiChatSession openNewSession(Long sellerId, String firstMessage) {
        AiChatSession s = new AiChatSession();
        s.setSellerId(sellerId);
        String title = firstMessage == null ? "New chat" : firstMessage.trim();
        if (title.isEmpty()) title = "New chat";
        s.setTitle(title.length() > 60 ? title.substring(0, 60) + "…" : title);
        s.setUpdatedAt(LocalDateTime.now());
        return sessionRepository.save(s);
    }
}
