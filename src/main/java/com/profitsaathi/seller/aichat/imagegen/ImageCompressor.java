package com.profitsaathi.seller.aichat.imagegen;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;

/**
 * Image compression utility for AI chat history. Reduces image sizes for
 * memory replay without losing too much visual quality.
 *
 * <p>Strategy:
 * <ul>
 *   <li>Resize large images to max 1024×1024 (preserves aspect ratio)</li>
 *   <li>Compress JPEG at 85% quality</li>
 *   <li>Convert PNG to JPEG if no transparency</li>
 *   <li>Target: 50-150 KB per image (down from 500KB-4MB)</li>
 * </ul>
 *
 * <p>This allows keeping 5-10 images in chat history (~500KB-1.5MB total)
 * instead of hitting 20MB+ with uncompressed images.
 */
@Slf4j
@Component
public class ImageCompressor {

    /** Max dimension for chat history images. Larger images are downscaled. */
    private static final int MAX_DIMENSION = 1024;

    /** JPEG compression quality (0.0-1.0). 0.85 gives good balance. */
    private static final float JPEG_QUALITY = 0.85f;

    /**
     * Compresses an image for chat history replay. Returns compressed bytes
     * and the output MIME type (always image/jpeg for now).
     *
     * @param originalBytes Original image bytes
     * @param originalMime Original MIME type (image/jpeg, image/png, etc.)
     * @return Compressed image result
     * @throws IOException if image cannot be read or compressed
     */
    public ImageGenResult compress(byte[] originalBytes, String originalMime) throws IOException {
        // Read original image
        BufferedImage original;
        try (ByteArrayInputStream bais = new ByteArrayInputStream(originalBytes)) {
            original = ImageIO.read(bais);
            if (original == null) {
                throw new IOException("Could not decode image");
            }
        }

        // Resize if needed
        BufferedImage resized = resizeIfNeeded(original);

        // Compress to JPEG
        byte[] compressed = compressToJpeg(resized);

        // Log compression stats
        double ratio = (double) compressed.length / originalBytes.length;
        log.debug("Image compressed: {}KB → {}KB ({:.1f}% of original)",
                originalBytes.length / 1024,
                compressed.length / 1024,
                ratio * 100);

        return new ImageGenResult("image/jpeg", compressed);
    }

    /**
     * Resizes image if either dimension exceeds MAX_DIMENSION.
     * Preserves aspect ratio.
     */
    private BufferedImage resizeIfNeeded(BufferedImage original) {
        int width = original.getWidth();
        int height = original.getHeight();

        // No resize needed
        if (width <= MAX_DIMENSION && height <= MAX_DIMENSION) {
            return original;
        }

        // Calculate new dimensions (preserve aspect ratio)
        double scale = Math.min(
                (double) MAX_DIMENSION / width,
                (double) MAX_DIMENSION / height
        );
        int newWidth = (int) (width * scale);
        int newHeight = (int) (height * scale);

        log.debug("Resizing image: {}×{} → {}×{}", width, height, newWidth, newHeight);

        // Resize with high quality
        Image scaled = original.getScaledInstance(newWidth, newHeight, Image.SCALE_SMOOTH);
        BufferedImage resized = new BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = resized.createGraphics();
        g2d.drawImage(scaled, 0, 0, null);
        g2d.dispose();

        return resized;
    }

    /**
     * Compresses image to JPEG format with quality setting.
     */
    private byte[] compressToJpeg(BufferedImage image) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        // Get JPEG writer
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpeg");
        if (!writers.hasNext()) {
            throw new IOException("No JPEG writer available");
        }
        ImageWriter writer = writers.next();

        // Configure compression quality
        ImageWriteParam param = writer.getDefaultWriteParam();
        if (param.canWriteCompressed()) {
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(JPEG_QUALITY);
        }

        // Write compressed image
        try (ImageOutputStream ios = ImageIO.createImageOutputStream(baos)) {
            writer.setOutput(ios);
            writer.write(null, new IIOImage(image, null, null), param);
        } finally {
            writer.dispose();
        }

        return baos.toByteArray();
    }

    /**
     * Quick check if compression would be beneficial.
     * Returns true if image is large enough to warrant compression.
     */
    public boolean shouldCompress(byte[] imageBytes) {
        // Compress if larger than 200KB
        return imageBytes.length > 200 * 1024;
    }
}
