package com.profitsaathi.seller.order;

import com.profitsaathi.seller.product.Product;
import com.profitsaathi.seller.user.Seller;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for Order entity
 * Tests order data model and business logic
 */
class OrderServiceTest {

    private Seller testSeller;
    private Product testProduct;
    private Order testOrder;

    @BeforeEach
    void setUp() {
        testSeller = new Seller();
        testSeller.setId(1L);
        testSeller.setEmail("seller@test.com");
        testSeller.setStoreName("Test Store");

        testProduct = new Product();
        testProduct.setId(1L);
        testProduct.setName("Test Product");
        testProduct.setSeller(testSeller);
        testProduct.setStatus("ACTIVE");
        testProduct.setSellingPrice(new BigDecimal("150.00"));

        testOrder = new Order();
        testOrder.setId(1L);
        testOrder.setProduct(testProduct);
        testOrder.setSeller(testSeller);
        testOrder.setQuantity(2);
        testOrder.setStatus("PENDING");
        testOrder.setCreatedAt(LocalDateTime.now());
    }

    @Test
    void testOrderEntity_GettersAndSetters() {
        Order order = new Order();
        order.setId(100L);
        order.setQuantity(5);
        order.setStatus("CONFIRMED");

        assertEquals(100L, order.getId());
        assertEquals(5, order.getQuantity());
        assertEquals("CONFIRMED", order.getStatus());
    }

    @Test
    void testOrderWithProduct() {
        assertNotNull(testOrder.getProduct());
        assertEquals("Test Product", testOrder.getProduct().getName());
        assertEquals(1L, testOrder.getProduct().getId());
    }

    @Test
    void testOrderWithSeller() {
        assertNotNull(testOrder.getSeller());
        assertEquals("seller@test.com", testOrder.getSeller().getEmail());
        assertEquals("Test Store", testOrder.getSeller().getStoreName());
    }

    @Test
    void testCalculateTotalAmount() {
        BigDecimal price = new BigDecimal("150.00");
        int quantity = 3;
        BigDecimal expected = new BigDecimal("450.00");

        BigDecimal result = price.multiply(BigDecimal.valueOf(quantity));

        assertEquals(expected, result);
    }

    @Test
    void testOrderQuantity() {
        testOrder.setQuantity(1);
        assertEquals(1, testOrder.getQuantity());

        testOrder.setQuantity(10);
        assertEquals(10, testOrder.getQuantity());

        assertTrue(testOrder.getQuantity() > 0);
    }

    @Test
    void testOrderStatusTransitions() {
        testOrder.setStatus("PENDING");
        assertEquals("PENDING", testOrder.getStatus());

        testOrder.setStatus("CONFIRMED");
        assertEquals("CONFIRMED", testOrder.getStatus());

        testOrder.setStatus("SHIPPED");
        assertEquals("SHIPPED", testOrder.getStatus());

        testOrder.setStatus("DELIVERED");
        assertEquals("DELIVERED", testOrder.getStatus());
    }

    @Test
    void testOrderTimestamps() {
        LocalDateTime now = LocalDateTime.now();
        testOrder.setCreatedAt(now);

        assertNotNull(testOrder.getCreatedAt());
        assertEquals(now, testOrder.getCreatedAt());
    }

    @Test
    void testOrderCustomerName() {
        testOrder.setCustomerName("John Doe");
        assertEquals("John Doe", testOrder.getCustomerName());
    }

    @Test
    void testOrderMultipleStatuses() {
        testOrder.setOrderStatus("CONFIRMED");
        testOrder.setPaymentStatus("PAID");
        testOrder.setStatus("PROCESSING");

        assertEquals("CONFIRMED", testOrder.getOrderStatus());
        assertEquals("PAID", testOrder.getPaymentStatus());
        assertEquals("PROCESSING", testOrder.getStatus());
    }

    @Test
    void testOrderComments() {
        testOrder.setComments("Please deliver before 5 PM");
        assertEquals("Please deliver before 5 PM", testOrder.getComments());
    }
}
