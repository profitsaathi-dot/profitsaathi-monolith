package com.profitsaathi.seller.offer;

import com.profitsaathi.seller.product.Product;
import com.profitsaathi.seller.product.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/v1/offers")
@RequiredArgsConstructor
public class OfferController {

    private final OfferRepository offerRepository;
    private final ProductRepository productRepository;

    @GetMapping("/product/{productId}")
    public List<OfferResponse> getOffersByProduct(@PathVariable Long productId) {
        return offerRepository.findByProduct_IdAndActiveTrue(productId).stream()
                .map(o -> {
                    OfferResponse r = new OfferResponse();
                    r.setOfferId(o.getId());
                    r.setName(o.getName());
                    r.setIcon(o.getIcon());
                    r.setPrice(o.getPrice());
                    r.setStockLimit(o.getStockLimit());
                    r.setSold(o.getSold());
                    r.setEndTime(o.getEndTime());
                    return r;
                })
                .toList();
    }

    @PostMapping
    public ResponseEntity<?> createOffer(@RequestBody CreateOfferRequest request) {
        Optional<Product> productOpt = productRepository.findById(request.getProductId());
        if (productOpt.isEmpty()) {
            return ResponseEntity.badRequest().body("Product not found");
        }

        Offer offer = new Offer();
        offer.setProduct(productOpt.get());
        offer.setName(request.getName());
        offer.setIcon(request.getIcon());
        offer.setPrice(request.getPrice());
        offer.setEndTime(request.getEndTime());
        offer.setStockLimit(request.getStockLimit());
        offer.setSold(request.getSold());
        offer.setActive(true);

        offerRepository.save(offer);
        return ResponseEntity.ok("Offer created successfully");
    }
}
