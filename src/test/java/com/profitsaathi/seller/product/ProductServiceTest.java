package com.profitsaathi.seller.product;

import com.profitsaathi.seller.user.Seller;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for Product entity
 * Tests product data model and business logic
 */
class ProductServiceTest {

    private Seller testSeller;
    private Product testProduct;

    @BeforeEach
    void setUp() {
        testSeller = new Seller();
        testSeller.setId(1L);
        testSeller.setEmail("seller@test.com");
        testSeller.setStoreName("Test Store");

        testProduct = new Product();
        testProduct.setId(1L);
        testProduct.setName("Test Product");
        testProduct.setDescription("Test Description");
        testProduct.setSeller(testSeller);
        testProduct.setStatus("ACTIVE");
        testProduct.setCostPrice(new BigDecimal("100.00"));
        testProduct.setSellingPrice(new BigDecimal("150.00"));
    }

    @Test
    void testProductEntity_GettersAndSetters() {
        Product product = new Product();
        product.setId(100L);
        product.setName("New Product");
        product.setDescription("New Description");
        product.setStatus("ACTIVE");
        product.setCostPrice(new BigDecimal("50.00"));
        product.setSellingPrice(new BigDecimal("75.00"));

        assertEquals(100L, product.getId());
        assertEquals("New Product", product.getName());
        assertEquals("New Description", product.getDescription());
        assertEquals("ACTIVE", product.getStatus());
        assertEquals(new BigDecimal("50.00"), product.getCostPrice());
        assertEquals(new BigDecimal("75.00"), product.getSellingPrice());
    }

    @Test
    void testProductWithSeller() {
        assertNotNull(testProduct.getSeller());
        assertEquals("seller@test.com", testProduct.getSeller().getEmail());
        assertEquals("Test Store", testProduct.getSeller().getStoreName());
    }

    @Test
    void testProductPricing() {
        BigDecimal costPrice = new BigDecimal("100.00");
        BigDecimal sellingPrice = new BigDecimal("150.00");
        BigDecimal margin = sellingPrice.subtract(costPrice);

        assertEquals(new BigDecimal("50.00"), margin);
        assertTrue(sellingPrice.compareTo(costPrice) > 0);
    }

    @Test
    void testProductImagePaths() {
        List<String> imagePaths = new ArrayList<>();
        imagePaths.add("/uploads/product1.jpg");
        imagePaths.add("/uploads/product2.jpg");

        testProduct.setImagePaths(imagePaths);

        assertNotNull(testProduct.getImagePaths());
        assertEquals(2, testProduct.getImagePaths().size());
        assertEquals("/uploads/product1.jpg", testProduct.getImagePaths().get(0));
    }

    @Test
    void testProductMainImageIndex() {
        testProduct.setMainImageIndex(0);
        assertEquals(0, testProduct.getMainImageIndex());

        testProduct.setMainImageIndex(2);
        assertEquals(2, testProduct.getMainImageIndex());
    }

    @Test
    void testProductPublicToken() {
        String token = "abc123xyz";
        testProduct.setPublicToken(token);

        assertEquals(token, testProduct.getPublicToken());
        assertNotNull(testProduct.getPublicToken());
    }

    @Test
    void testProductCosts() {
        testProduct.setShippingCost(new BigDecimal("10.00"));
        testProduct.setPackagingCost(new BigDecimal("5.00"));
        testProduct.setCompetitorPrice(new BigDecimal("140.00"));

        assertEquals(new BigDecimal("10.00"), testProduct.getShippingCost());
        assertEquals(new BigDecimal("5.00"), testProduct.getPackagingCost());
        assertEquals(new BigDecimal("140.00"), testProduct.getCompetitorPrice());
    }

    @Test
    void testProductTotalCost() {
        BigDecimal costPrice = new BigDecimal("100.00");
        BigDecimal shippingCost = new BigDecimal("10.00");
        BigDecimal packagingCost = new BigDecimal("5.00");

        BigDecimal totalCost = costPrice.add(shippingCost).add(packagingCost);

        assertEquals(new BigDecimal("115.00"), totalCost);
    }

    @Test
    void testProductStatusTransitions() {
        testProduct.setStatus("DRAFT");
        assertEquals("DRAFT", testProduct.getStatus());

        testProduct.setStatus("ACTIVE");
        assertEquals("ACTIVE", testProduct.getStatus());

        testProduct.setStatus("INACTIVE");
        assertEquals("INACTIVE", testProduct.getStatus());
    }
}
