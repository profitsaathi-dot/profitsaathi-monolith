package com.profitsaathi.customer.cart;

import com.profitsaathi.seller.product.Product;
import com.profitsaathi.seller.product.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Cart resolves products via {@link ProductRepository} directly — no Feign,
 * no service discovery. The buyer-side Cart and the seller-side Product live
 * in the same JVM.
 */
@Service
@RequiredArgsConstructor
public class CartService {

    private final CartItemRepository cartItemRepository;
    private final ProductRepository productRepository;

    public List<CartItemResponseDTO> getCartForCustomer(Long customerId) {
        return cartItemRepository.findByCustomerId(customerId).stream()
                .map(item -> {
                    Product product = productRepository.findById(item.getProductId()).orElse(null);

                    CartItemResponseDTO response = new CartItemResponseDTO();
                    response.setId(item.getId());
                    response.setCustomerId(item.getCustomerId());
                    response.setName(item.getName());
                    response.setQty(item.getQty());

                    Map<String, Object> productDetails = new HashMap<>();
                    if (product != null) {
                        productDetails.put("id", product.getId());
                        productDetails.put("sellingPrice", product.getSellingPrice());
                        productDetails.put("mainImageUrl",
                                product.getImagePaths() != null && !product.getImagePaths().isEmpty()
                                        ? "/api/v1/products/" + product.getId() + "/image?index=" + product.getMainImageIndex()
                                        : null);
                    } else {
                        productDetails.put("id", item.getProductId());
                    }
                    response.setProductDetails(productDetails);
                    return response;
                })
                .toList();
    }

    @Transactional
    public CartItem addOrUpdateItem(Long customerId, CartItem request) {
        if (request.getProductId() == null) {
            throw new RuntimeException("Product ID cannot be null");
        }

        return cartItemRepository
                .findByCustomerIdAndProductId(customerId, request.getProductId())
                .map(existing -> {
                    existing.setQty(request.getQty());
                    existing.setProductId(request.getProductId());
                    if (request.getName() != null) existing.setName(request.getName());
                    return cartItemRepository.save(existing);
                })
                .orElseGet(() -> {
                    request.setCustomerId(customerId);
                    return cartItemRepository.save(request);
                });
    }

    @Transactional
    public void removeItem(Long itemId, Long customerId) {
        cartItemRepository.findById(itemId).ifPresent(item -> {
            if (customerId.equals(item.getCustomerId())) {
                cartItemRepository.delete(item);
            }
        });
    }
}
