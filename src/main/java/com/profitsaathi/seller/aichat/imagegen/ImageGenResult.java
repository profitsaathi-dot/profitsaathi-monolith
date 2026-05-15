package com.profitsaathi.seller.aichat.imagegen;

/**
 * Raw bytes of a generated image plus its mime type. Stored on disk by the
 * service layer; bytes never travel through the database.
 */
public record ImageGenResult(String mimeType, byte[] bytes) {

    public static ImageGenResult png(byte[] bytes) { return new ImageGenResult("image/png", bytes); }

    public static ImageGenResult jpeg(byte[] bytes) { return new ImageGenResult("image/jpeg", bytes); }
}
