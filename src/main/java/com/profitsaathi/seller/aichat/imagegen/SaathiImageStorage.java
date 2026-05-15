package com.profitsaathi.seller.aichat.imagegen;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Disk storage for assistant-generated images. Each Saathi-generated image
 * is saved as {@code {dir}/saathi-images/seller{sellerId}/{messageId}.{ext}}
 * — predictable enough that we can serve via the message id alone.
 *
 * Lives outside the DB on purpose: a 1024×1024 image is ~150 KB, and
 * 5 generations/day × hundreds of sellers would balloon the table fast.
 */
@Component
@RequiredArgsConstructor
public class SaathiImageStorage {

    @Value("${product.image.upload-dir:uploads/products}")
    private String baseUploadDir;

    /** Persists the image bytes and returns the filename relative to {@code baseUploadDir}. */
    public String save(Long sellerId, Long messageId, ImageGenResult image) throws IOException {
        String ext = mimeToExt(image.mimeType());
        String relative = "saathi-images/seller" + sellerId + "/" + messageId + ext;
        Path target = Paths.get(baseUploadDir, relative);
        Files.createDirectories(target.getParent());
        Files.write(target, image.bytes());
        return relative;
    }

    /** Opens a stream over a previously-saved file. Caller must close. */
    public InputStream open(String relativePath) throws IOException {
        Path p = Paths.get(baseUploadDir, relativePath);
        return Files.newInputStream(p);
    }

    /**
     * Loads the bytes of a previously-saved file. Used to replay prior
     * user-uploaded images into chat history so multimodal models can keep
     * "remembering" the product photo across turns.
     */
    public byte[] loadBytes(String relativePath) throws IOException {
        return Files.readAllBytes(Paths.get(baseUploadDir, relativePath));
    }

    public boolean exists(String relativePath) {
        return relativePath != null && Files.exists(Paths.get(baseUploadDir, relativePath));
    }

    private static String mimeToExt(String mime) {
        if (mime == null) return ".png";
        String m = mime.toLowerCase();
        if (m.contains("jpeg") || m.contains("jpg")) return ".jpg";
        if (m.contains("webp")) return ".webp";
        return ".png";
    }
}
