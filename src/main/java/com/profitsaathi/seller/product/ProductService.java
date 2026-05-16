package com.profitsaathi.seller.product;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.profitsaathi.config.ProductConfig;
import com.profitsaathi.seller.user.Seller;
import com.profitsaathi.seller.user.SellerRepository;
import com.profitsaathi.util.aes.AESService;
import lombok.AllArgsConstructor;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.core.io.InputStreamResource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

@AllArgsConstructor
@Service
public class ProductService {

    private final ProductRepository productRepository;
    private final SellerRepository sellerRepository;
    private final AESService aesService;
    private final ObjectMapper objectMapper;
    private final ProductConfig productConfig;
   // private final CacheManager cacheManager;

    @Transactional
    public void addProduct(Long sellerId, String encryptedJson, List<MultipartFile> media, int mainImageIndex) throws Exception {
        Seller seller = sellerRepository.findById(sellerId)
                .orElseThrow(() -> new RuntimeException("Seller not registered"));

        String decryptedJson = aesService.decryptToJson(encryptedJson);
        if (decryptedJson == null || decryptedJson.isEmpty()) throw new RuntimeException("Decryption returned empty");

        ProductDTO dto = objectMapper.readValue(decryptedJson, ProductDTO.class);

        if (productRepository.findByNameIgnoreCaseAndSellerId(dto.getName(), seller.getId()).isPresent()) {
            throw new RuntimeException("This Product is already added");
        }

        Product entity = new Product();
        entity.setName(dto.getName());
        entity.setDescription(dto.getDescription());
        entity.setSeller(seller);
        entity.setStatus(dto.getStatus());
        entity.setCostPrice(dto.getCostPrice());
        entity.setShippingCost(dto.getShippingCost());
        entity.setPackagingCost(dto.getPackagingCost());
        entity.setCompetitorPrice(dto.getCompetitorPrice());
        entity.setPublicToken(UUID.randomUUID().toString().replace("-", ""));
        entity.setSellingPrice(dto.getSellingPrice());

        // Updated: Handle media (images + videos)
        List<String> mediaPaths = saveMedia(media, new ArrayList<>());
        entity.setImagePaths(mediaPaths);
        entity.setMainImageIndex((mainImageIndex >= 0 && mainImageIndex < mediaPaths.size()) ? mainImageIndex : 0);

        productRepository.save(entity);
        //evictSellerCache(seller.getId());
    }

    @Transactional
    public void updateProduct(String encryptedJson, List<MultipartFile> media, int mainImageIndex) throws Exception {
        String decryptedJson = aesService.decryptToJson(encryptedJson);
        if (decryptedJson == null || decryptedJson.isEmpty()) throw new RuntimeException("Invalid request data");

        Product decoded = objectMapper.readValue(decryptedJson, Product.class);

        Product existing = productRepository.findById(decoded.getId())
                .orElseThrow(() -> new RuntimeException("Product not found"));

        Long sellerId = existing.getSeller().getId();

        if (productRepository.findByNameIgnoreCaseAndSellerIdAndIdNot(decoded.getName(), sellerId, decoded.getId()).isPresent()) {
            throw new RuntimeException("Product with same name already exists");
        }

        existing.setName(decoded.getName());
        existing.setDescription(decoded.getDescription());
        existing.setStatus(decoded.getStatus());
        existing.setCostPrice(decoded.getCostPrice());
        existing.setShippingCost(decoded.getShippingCost());
        existing.setPackagingCost(decoded.getPackagingCost());
        existing.setCompetitorPrice(decoded.getCompetitorPrice());
        existing.setSellingPrice(decoded.getSellingPrice());

        List<String> currentMedia = existing.getImagePaths() != null
                ? new ArrayList<>(existing.getImagePaths()) : new ArrayList<>();

        // Updated: Handle media during update
        List<String> updated = saveMedia(media, currentMedia);
        existing.setImagePaths(updated);

        if (updated != null && mainImageIndex >= 0 && mainImageIndex < updated.size()) {
            existing.setMainImageIndex(mainImageIndex);
        }

        productRepository.save(existing);
        evictSellerCache(sellerId);
    }

    /**
     * Unified method to save both Images and Videos
     */
    private List<String> saveMedia(List<MultipartFile> media, List<String> existingPaths) throws IOException {
        if (media == null || media.isEmpty()) return existingPaths;

        // Allowed max 6 items (as per your frontend .slice(0,6))
        for (int i = 0; i < Math.min(media.size(), 6); i++) {
            MultipartFile file = media.get(i);
            if (file != null && !file.isEmpty()) {
                String filename = buildSafeFilename(file, i);
                Path path = Paths.get(productConfig.getUploadDir(), filename);
                Files.createDirectories(path.getParent());
                Files.write(path, file.getBytes());

                if (existingPaths.size() > i) existingPaths.set(i, filename);
                else existingPaths.add(filename);
            }
        }
        return existingPaths;
    }

    private static String buildSafeFilename(MultipartFile file, int index) {
        String original = file.getOriginalFilename() != null ? file.getOriginalFilename() : "file";
        String ext = "";
        int dot = original.lastIndexOf('.');
        if (dot >= 0 && dot < original.length() - 1) ext = original.substring(dot).toLowerCase();

        // If extension is missing or weird, guess from content type
        if (ext.isEmpty() || ext.length() > 6) ext = extensionFromContentType(file.getContentType());

        String base = original.substring(0, dot >= 0 ? dot : original.length())
                .replaceAll("[^A-Za-z0-9_-]", "_");
        if (base.isEmpty()) base = "media";

        return System.currentTimeMillis() + "_" + index + "_" + base + ext;
    }

    private static String extensionFromContentType(String contentType) {
        if (contentType == null) return ".jpg";
        String ct = contentType.toLowerCase();
        if (ct.contains("png")) return ".png";
        if (ct.contains("gif")) return ".gif";
        if (ct.contains("webp")) return ".webp";
        if (ct.contains("mp4")) return ".mp4";
        if (ct.contains("quicktime")) return ".mov";
        if (ct.contains("video/")) return ".mp4"; // Default video extension
        return ".jpg";
    }



    // ... Keep all other methods (detectContentType, getProductImage, mapToSimpleMap, etc.) exactly as they were ...

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getSimpleProductsForSeller(Long sellerId) {
        return getCachedSimpleProducts(sellerId);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getSimpleProductsForPublic(String publicToken) {
        Seller seller = sellerRepository.findByPublicToken(publicToken)
                .orElseThrow(() -> new RuntimeException("Seller not found"));
        if (seller.getStatus() != Seller.Status.ACTIVE) {
            throw new RuntimeException("Seller not active");
        }
        return getCachedSimpleProducts(seller.getId());
    }

    @Cacheable(value = "sellerProducts", key = "#sellerId")
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getCachedSimpleProducts(Long sellerId) {
        List<Product> products = productRepository.findBySellerIdAndStatus(sellerId, "ACTIVE");
        return products.stream().map(this::mapToSimpleMap).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getAllProductsForSeller(Long sellerId) {
        return productRepository.findBySellerIdOrderByIdDesc(sellerId).stream()
                .map(this::mapToSimpleMap)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getPublicProductDetails(String token) {
        Product product = productRepository.findByPublicTokenAndStatus(token, "ACTIVE")
                .orElseThrow(() -> new RuntimeException("Product not found"));
        return mapToSimpleMap(product);
    }

    @Transactional(readOnly = true)
    public Map.Entry<String, InputStreamResource> getProductImage(Long id, int index) throws IOException {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Product not found"));
        List<String> images = product.getImagePaths();

        if (images == null || images.isEmpty() || index >= images.size()) {
            throw new RuntimeException("Image not found");
        }

        Path imagePath = Paths.get(productConfig.getUploadDir(), images.get(index));
        if (!Files.exists(imagePath)) throw new RuntimeException("File does not exist");

        InputStreamResource resource = new InputStreamResource(Files.newInputStream(imagePath));
        return new AbstractMap.SimpleEntry<>(detectContentType(imagePath), resource);
    }

    private static String detectContentType(Path imagePath) throws IOException {
        String ct = Files.probeContentType(imagePath);
        if (ct != null && !ct.isBlank()) return ct;
        ct = java.net.URLConnection.guessContentTypeFromName(imagePath.getFileName().toString());
        if (ct != null && !ct.isBlank()) return ct;
        return "application/octet-stream";
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getProductById(Long id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Product not found"));
        return mapToSimpleMap(product);
    }

    @Transactional(readOnly = true)
    public Page<Product> getProductsBySeller(Long sellerId, Pageable pageable) {
        return productRepository.findBySellerId(sellerId, pageable);
    }

    private void evictSellerCache(Long sellerId) {
       // org.springframework.cache.Cache cache = cacheManager.getCache("sellerProducts");
        //if (cache != null) cache.evict(sellerId);
    }

    private Map<String, Object> mapToSimpleMap(Product p) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", p.getId());
        map.put("name", p.getName());
        map.put("description", p.getDescription());
        map.put("status", p.getStatus());
        map.put("costPrice", p.getCostPrice());
        map.put("shippingCost", p.getShippingCost());
        map.put("packagingCost", p.getPackagingCost());
        map.put("competitorPrice", p.getCompetitorPrice());
        map.put("public_token", p.getPublicToken());
        map.put("sellingPrice", p.getSellingPrice());
        map.put("mainImageindex",p.getMainImageIndex());
        return map;
    }
}