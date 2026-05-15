package com.profitsaathi.seller.aichat;

/**
 * Provider-agnostic chat turn. Role is {@code "user"} or {@code "model"}/{@code "assistant"} —
 * each provider impl normalises to its own wire-format role.
 */
public record ChatTurn(String role, String text, String imageMime, String imageBase64) {

    public static ChatTurn user(String text) {
        return new ChatTurn("user", text, null, null);
    }

    public static ChatTurn userWithImage(String text, String mime, String b64) {
        return new ChatTurn("user", text, mime, b64);
    }

    public static ChatTurn assistant(String text) {
        return new ChatTurn("assistant", text, null, null);
    }

    public boolean hasImage() {
        return imageBase64 != null && !imageBase64.isBlank();
    }

    public boolean isUser() {
        return "user".equalsIgnoreCase(role);
    }
}
