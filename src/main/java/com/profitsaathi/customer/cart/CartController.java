package com.profitsaathi.customer.cart;

import com.profitsaathi.auth.AuthenticatedPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/cart")
@RequiredArgsConstructor
@PreAuthorize("hasRole('CUSTOMER')")
public class CartController {

    private final CartService cartService;

    @GetMapping
    public ResponseEntity<List<CartItemResponseDTO>> getCart(@AuthenticationPrincipal AuthenticatedPrincipal me) {
        return ResponseEntity.ok(cartService.getCartForCustomer(me.subjectId()));
    }

    @PutMapping
    public ResponseEntity<CartItem> updateCartItem(@AuthenticationPrincipal AuthenticatedPrincipal me,
                                                   @RequestBody CartItem item) {
        return ResponseEntity.ok(cartService.addOrUpdateItem(me.subjectId(), item));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> removeCartItem(@AuthenticationPrincipal AuthenticatedPrincipal me,
                                               @PathVariable Long id) {
        cartService.removeItem(id, me.subjectId());
        return ResponseEntity.noContent().build();
    }
}
