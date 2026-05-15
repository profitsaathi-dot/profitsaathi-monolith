/**
 * <h2>Saathi AI — seller-facing chat + image assistant</h2>
 *
 * Helps small Indian sellers turn product photos into social-media-ready
 * content (captions, hashtags, Reel-song ideas, post concepts) and generate
 * or edit product imagery. Sellers BYO API keys for Gemini, OpenRouter, and
 * NVIDIA NIM — ProfitSaathi pays $0 for AI compute.
 *
 * <h3>Request flow</h3>
 *
 * <pre>
 *
 *   Browser (/chat)
 *       │
 *       │  POST/GET /api/ai/chat/*       (proxy routes in seller-app/app/api/ai/chat/)
 *       ▼
 *   Next.js route handler
 *       │
 *       │  apiClient.fetch() with Bearer JWT
 *       ▼
 *   Spring  /api/v1/ai/saathi/**          ← {@link com.profitsaathi.seller.aichat.SaathiKillSwitchInterceptor}
 *       │                                    short-circuits with 503 when ai.saathi.enabled=false
 *       ▼
 *   {@link com.profitsaathi.seller.aichat.AiChatController}
 *       │                                  HTTP shell — auth, mapping exceptions to status codes
 *       ▼
 *   {@link com.profitsaathi.seller.aichat.SaathiAiService}
 *       │                                  Orchestration — single place to follow the flow
 *       │
 *       ├─► CHAT path
 *       │     • history fetch         (AiChatMessageRepository)
 *       │     • AiChatProviderRouter  → primary provider then fallbacks
 *       │           ├─ GeminiChatProvider
 *       │           ├─ OpenRouterChatProvider  (extends OpenAiCompatibleChatClient)
 *       │           └─ NvidiaChatProvider     (extends OpenAiCompatibleChatClient)
 *       │     • persist user + assistant turns
 *       │
 *       ├─► IMAGE GENERATE path  (no input image)
 *       │     • ImageGenRouter.generate(seller, prompt)
 *       │           ├─ NvidiaImageGenProvider  (FLUX schnell, free)
 *       │           └─ GeminiImageGenProvider  (gemini-3.1-flash-image-preview, paid)
 *       │     • SaathiImageStorage.save() → uploads/products/saathi-images/seller{id}/
 *       │     • persist message row with image_path
 *       │
 *       └─► IMAGE EDIT path  (input image present)
 *             • ImageGenRouter.editImage(seller, prompt, mime, base64)
 *                   ├─ NvidiaImageEditProvider  (FLUX Kontext, free)
 *                   └─ GeminiImageGenProvider.edit(...)  (paid fallback)
 *             • same storage + persistence as generate
 *
 * </pre>
 *
 * <h3>File index</h3>
 *
 * <h4>HTTP layer</h4>
 * <ul>
 *   <li>{@link com.profitsaathi.seller.aichat.AiChatController} — controller, 8 endpoints</li>
 *   <li>{@link com.profitsaathi.seller.aichat.AiChatDtos} — request/response records</li>
 *   <li>{@link com.profitsaathi.seller.aichat.SaathiKillSwitch} — feature flag bean</li>
 *   <li>{@link com.profitsaathi.seller.aichat.SaathiKillSwitchInterceptor} — global gate registered in WebConfig</li>
 *   <li>{@link com.profitsaathi.seller.aichat.NoAiProviderConfiguredException} → HTTP 412</li>
 *   <li>{@link com.profitsaathi.seller.aichat.AllAiProvidersFailedException} → HTTP 502</li>
 *   <li>{@link com.profitsaathi.seller.aichat.MissingApiKeyException} → router skips that provider silently</li>
 * </ul>
 *
 * <h4>Orchestration</h4>
 * <ul>
 *   <li>{@link com.profitsaathi.seller.aichat.SaathiAiService} — single entry point for every flow</li>
 *   <li>{@link com.profitsaathi.seller.aichat.AiChatProviderRouter} — chat fallback chain</li>
 *   <li>{@link com.profitsaathi.seller.aichat.imagegen.ImageGenRouter} — image-gen + edit fallback chains</li>
 * </ul>
 *
 * <h4>Chat providers</h4>
 * <ul>
 *   <li>{@link com.profitsaathi.seller.aichat.ChatProvider} — common contract</li>
 *   <li>{@link com.profitsaathi.seller.aichat.ChatTurn} — provider-agnostic message record</li>
 *   <li>{@link com.profitsaathi.seller.aichat.ChatSystemPrompt} — shared system instruction (locale-aware)</li>
 *   <li>{@link com.profitsaathi.seller.aichat.GeminiChatProvider}</li>
 *   <li>{@link com.profitsaathi.seller.aichat.OpenAiCompatibleChatClient} — base class</li>
 *   <li>{@link com.profitsaathi.seller.aichat.OpenRouterChatProvider}</li>
 *   <li>{@link com.profitsaathi.seller.aichat.NvidiaChatProvider}</li>
 *   <li>{@link com.profitsaathi.seller.aichat.AiProvider} — GEMINI / OPENROUTER / NVIDIA enum</li>
 * </ul>
 *
 * <h4>Image providers + storage</h4>
 * <ul>
 *   <li>{@link com.profitsaathi.seller.aichat.imagegen.ImageGenProvider} — common contract</li>
 *   <li>{@link com.profitsaathi.seller.aichat.imagegen.ImageGenResult} — (mime, bytes) record</li>
 *   <li>{@link com.profitsaathi.seller.aichat.imagegen.NvidiaImageGenProvider} — FLUX schnell text-to-image</li>
 *   <li>{@link com.profitsaathi.seller.aichat.imagegen.NvidiaImageEditProvider} — FLUX Kontext image-to-image</li>
 *   <li>{@link com.profitsaathi.seller.aichat.imagegen.GeminiImageGenProvider} — gemini-3.1-flash-image-preview, gen + edit</li>
 *   <li>{@link com.profitsaathi.seller.aichat.imagegen.SaathiImageStorage} — disk I/O for generated images</li>
 *   <li>{@link com.profitsaathi.seller.aichat.imagegen.LogScrub} — truncate base64 + bearer tokens in error logs</li>
 * </ul>
 *
 * <h4>Persistence</h4>
 * <ul>
 *   <li>{@link com.profitsaathi.seller.aichat.AiChatSession} + repository — one row per chat</li>
 *   <li>{@link com.profitsaathi.seller.aichat.AiChatMessage} + repository — turns; carries image_path for assistant-generated images</li>
 * </ul>
 *
 * <p>Per-seller provider keys live AES-encrypted on the {@code sellers}
 * table (columns {@code gemini_api_key}, {@code openrouter_api_key},
 * {@code nvidia_api_key}, plus {@code ai_primary_provider}) via the
 * existing {@code EncryptedStringConverter}. Generated image bytes live
 * on disk under {@code uploads/products/saathi-images/seller{id}/} and
 * are served back through the authenticated
 * {@code GET /api/v1/ai/saathi/image/{messageId}} endpoint.
 *
 * <h3>Configuration knobs</h3>
 * <ul>
 *   <li>{@code ai.saathi.enabled} (default {@code true}) — global kill-switch</li>
 *   <li>{@code ai.gemini-key} — fallback Gemini key when seller hasn't configured one</li>
 *   <li>{@code ai.request-timeout-seconds} — per-provider HTTP timeout</li>
 *   <li>{@code product.image.upload-dir} — base directory for the image store</li>
 * </ul>
 *
 * <h3>Things intentionally NOT here</h3>
 * <ul>
 *   <li><b>Daily-limit counters</b> — sellers BYO keys, providers enforce their own rate limits.</li>
 *   <li><b>Subscription gating</b> — add a {@code @PreAuthorize} on the controller when the
 *       free period ends; no counter infrastructure needed.</li>
 *   <li><b>OpenRouter image-gen</b> — their free image tier is unreliable; chat only.</li>
 *   <li><b>Pollinations</b> — moved to paid Pollen credits in 2026, no longer a free fallback.</li>
 * </ul>
 */
package com.profitsaathi.seller.aichat;
