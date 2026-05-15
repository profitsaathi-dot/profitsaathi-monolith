package com.profitsaathi.seller.aichat;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Wire-format DTOs for the Saathi AI API. Plain Java records — request
 * bodies and response shapes both. Daily-limit fields intentionally absent
 * (see {@link SaathiAiService} header for rationale).
 */
public final class AiChatDtos {

    private AiChatDtos() {}

    public record SendRequest(Long sessionId, String message, String imageBase64, String imageMime) {}

    public record SendResponse(Long sessionId, String reply, String providerUsed) {}

    public record SessionSummary(Long id, String title, LocalDateTime createdAt, LocalDateTime updatedAt) {}

    /**
     * @param imagePath     non-null on Saathi-generated assistant turns;
     *                      the frontend renders it via
     *                      {@code GET /api/v1/ai/saathi/image/{id}}.
     * @param imageMime     mime type of the persisted image (null for non-image turns)
     * @param imageProvider provider that generated the image (NVIDIA / GEMINI)
     * @param chatProvider  provider that produced this assistant text turn
     *                      (GEMINI / OPENROUTER / NVIDIA). Null on user turns
     *                      and on old rows from before the column existed.
     */
    public record MessageView(
            Long id,
            String role,
            String content,
            boolean hasImage,
            String imagePath,
            String imageMime,
            String imageProvider,
            String chatProvider,
            LocalDateTime createdAt
    ) {}

    public record SessionDetail(SessionSummary session, List<MessageView> messages) {}

    public record ProviderStatus(
            boolean geminiKeySet,
            boolean openrouterKeySet,
            boolean nvidiaKeySet,
            String primaryProvider,
            List<String> configured
    ) {}

    public record SaveKeyRequest(String provider, String apiKey) {}

    public record SetPrimaryRequest(String provider) {}

    /**
     * @param imageBase64 optional input image — when present the request is
     *                    treated as an edit (image-to-image) instead of a
     *                    fresh text-to-image generation.
     * @param imageMime   mime type of {@code imageBase64} — required alongside it.
     */
    public record GenerateImageRequest(Long sessionId, String prompt, String imageBase64, String imageMime) {}

    /**
     * Returned synchronously after a successful image gen/edit. Image bytes
     * are NOT inlined — clients fetch them by id from
     * {@code GET /api/v1/ai/saathi/image/{messageId}} so the JSON stays small.
     */
    public record GeneratedImageResponse(
            Long sessionId,
            Long messageId,
            String providerUsed,
            String mimeType
    ) {}
}
