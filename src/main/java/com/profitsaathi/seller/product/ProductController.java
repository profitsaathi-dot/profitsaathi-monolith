package com.profitsaathi.seller.product;

import com.profitsaathi.auth.AuthenticatedPrincipal;
import lombok.AllArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/v1/products")
@AllArgsConstructor
public class ProductController {

    private final ProductService productService;

    @PostMapping
    public ResponseEntity<?> add(
            @AuthenticationPrincipal AuthenticatedPrincipal me,
            @RequestParam("request") String encryptedJson,
            @RequestParam(required = false, name = "media") List<MultipartFile> media,
            @RequestParam(defaultValue = "0") int mainImageIndex) {
        try {
            // Your service can now handle a single list containing both images and videos
            productService.addProduct(me.subjectId(), encryptedJson, media, mainImageIndex);
            return ResponseEntity.status(201)
                    .body(Collections.singletonMap("message", "Created Successfully"));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Collections.singletonMap("message", e.getMessage()));
        }
    }

    @PutMapping
    public ResponseEntity<?> update(
            @AuthenticationPrincipal AuthenticatedPrincipal me, // Added for consistency
            @RequestParam("request") String encryptedJson,
            @RequestParam(required = false, name = "media") List<MultipartFile> media,
            @RequestParam(defaultValue = "0") int mainImageIndex) {
        try {
            productService.updateProduct(encryptedJson, media, mainImageIndex);
            return ResponseEntity.ok(Collections.singletonMap("message", "Updated Successfully"));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Collections.singletonMap("message", e.getMessage()));
        }
    }

    @GetMapping("/user/simple")
    public ResponseEntity<?> getSimpleProducts(@AuthenticationPrincipal AuthenticatedPrincipal me) {
        try {
            return ResponseEntity.ok(productService.getSimpleProductsForSeller(me.subjectId()));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Collections.singletonMap("message", e.getMessage()));
        }
    }

    @GetMapping("/user/all")
    public ResponseEntity<?> getAllProducts(@AuthenticationPrincipal AuthenticatedPrincipal me) {
        try {
            return ResponseEntity.ok(productService.getAllProductsForSeller(me.subjectId()));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Collections.singletonMap("message", e.getMessage()));
        }
    }

    @GetMapping("/user/public-products")
    public ResponseEntity<?> getPublicProducts(@RequestParam String token) {
        try {
            return ResponseEntity.ok(productService.getSimpleProductsForPublic(token));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Collections.singletonMap("message", e.getMessage()));
        }
    }

    @GetMapping("/public")
    public ResponseEntity<?> getByToken(@RequestParam String token) {
        try {
            return ResponseEntity.ok(productService.getPublicProductDetails(token));
        } catch (Exception e) {
            return ResponseEntity.status(404).body(Collections.singletonMap("message", e.getMessage()));
        }
    }

    @GetMapping("/{id}/image")
    public ResponseEntity<Resource> getProductImage(@PathVariable Long id,
                                                    @RequestParam(defaultValue = "0") int index) {
        try {
            Map.Entry<String, InputStreamResource> r = productService.getProductImage(id, index);
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(r.getKey()))
                    .cacheControl(org.springframework.http.CacheControl.maxAge(30, TimeUnit.DAYS))
                    .body(r.getValue());
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/user/{sellerId}")
    public Page<Product> getProductsBySeller(@PathVariable Long sellerId,
                                             @RequestParam(defaultValue = "0") int page,
                                             @RequestParam(defaultValue = "50") int size) {
        return productService.getProductsBySeller(sellerId, PageRequest.of(page, size));
    }

    @GetMapping("/id/{id}")
    public ResponseEntity<?> getProductById(@PathVariable Long id) {
        return ResponseEntity.ok(productService.getProductById(id));
    }

    @GetMapping("/public/id/{id}")
    public ResponseEntity<?> getPublicProduct(@PathVariable Long id) {
        return ResponseEntity.ok(productService.getProductById(id));
    }
}
