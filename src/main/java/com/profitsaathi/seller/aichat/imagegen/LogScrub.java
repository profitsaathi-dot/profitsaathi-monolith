package com.profitsaathi.seller.aichat.imagegen;

/**
 * Small helper to keep base64 image bytes and the seller's API key out of
 * error logs. Provider error responses often echo the submitted request,
 * which can include a 4 MB base64 image — left raw, that bloats the log
 * file by megabytes per error and (worse) puts a recognisable seller-uploaded
 * image into the ops-team-readable log stream.
 *
 * Centralised so every provider scrubs the same way.
 */
public final class LogScrub {

    /** Max length of any response-body string we log. */
    private static final int MAX_BODY_LEN = 500;

    private LogScrub() {}

    /**
     * Truncate to {@link #MAX_BODY_LEN} chars and mask any obvious base64
     * blobs / bearer tokens. Cheap and good-enough for ops log hygiene —
     * we're not trying to be cryptographically thorough, just to stop the
     * loudest leaks.
     */
    public static String truncate(String body) {
        if (body == null) return "";
        // Common shape: "...\"data\":\"<huge base64>\"...". Replace the value
        // inside any "data": "..." or "base64": "..." pair with a placeholder
        // before truncating so we never log image bytes.
        String scrubbed = body
                .replaceAll("\"data\"\\s*:\\s*\"[^\"]{200,}\"",     "\"data\":\"<scrubbed-base64>\"")
                .replaceAll("\"base64\"\\s*:\\s*\"[^\"]{200,}\"",   "\"base64\":\"<scrubbed-base64>\"")
                .replaceAll("\"image\"\\s*:\\s*\"[^\"]{200,}\"",    "\"image\":\"<scrubbed-base64>\"")
                .replaceAll("Bearer\\s+[A-Za-z0-9_\\-]{20,}",       "Bearer <scrubbed>");

        return scrubbed.length() <= MAX_BODY_LEN
                ? scrubbed
                : scrubbed.substring(0, MAX_BODY_LEN) + "…(truncated)";
    }
}
