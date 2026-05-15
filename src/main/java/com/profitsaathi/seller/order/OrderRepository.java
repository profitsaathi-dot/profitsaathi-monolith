package com.profitsaathi.seller.order;

import com.profitsaathi.seller.user.Seller;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.Optional;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {

    /**
     * Gross revenue for the given seller-month — sum of {@code unitPrice ×
     * quantity} across all orders booked that calendar month.
     *
     * <p>This is the correct "sales" figure. The legacy
     * {@link #getTotalSalesForMonth} accidentally summed {@code totalCost}
     * (which the OrderService sets to {@code costPrice × quantity}, i.e. COGS),
     * so every monthly summary it produced had {@code netProfit = 0} and a
     * health score of 30. Use this method instead.
     */
    @Query("""
        SELECT COALESCE(SUM(o.unitPrice * o.quantity), 0)
        FROM Order o
        WHERE o.seller = :seller
          AND MONTH(o.createdAt) = :month
          AND YEAR(o.createdAt) = :year
    """)
    BigDecimal getRevenueForMonth(@Param("seller") Seller seller,
                                  @Param("month") int month,
                                  @Param("year") int year);

    /**
     * @deprecated bug: sums {@code totalCost} (COGS), not sales. Use
     *     {@link #getRevenueForMonth} for actual revenue. Kept temporarily
     *     so admin/legacy callers don't break — remove once they migrate.
     */
    @Deprecated
    @Query("""
        SELECT COALESCE(SUM(o.totalCost), 0)
        FROM Order o
        WHERE o.seller = :seller
          AND MONTH(o.createdAt) = :month
          AND YEAR(o.createdAt) = :year
    """)
    BigDecimal getTotalSalesForMonth(@Param("seller") Seller seller,
                                     @Param("month") int month,
                                     @Param("year") int year);

    /**
     * COGS for the given seller-month — sum of order {@code totalCost}, which
     * {@code OrderService.createOrder} populates as {@code costPrice ×
     * quantity}. Name kept for callers; semantically this is cost of goods
     * sold, not general business expenses.
     */
    @Query("""
        SELECT COALESCE(SUM(o.totalCost), 0)
        FROM Order o
        WHERE o.seller = :seller
          AND MONTH(o.createdAt) = :month
          AND YEAR(o.createdAt) = :year
    """)
    BigDecimal getTotalExpensesForMonth(@Param("seller") Seller seller,
                                        @Param("month") int month,
                                        @Param("year") int year);

    /** Revenue between two timestamps — used by the weekly report flow. */
    @Query("""
        SELECT COALESCE(SUM(o.unitPrice * o.quantity), 0)
        FROM Order o
        WHERE o.seller = :seller
          AND o.createdAt >= :from
          AND o.createdAt <  :until
    """)
    BigDecimal getRevenueBetween(@Param("seller") Seller seller,
                                 @Param("from") java.time.LocalDateTime from,
                                 @Param("until") java.time.LocalDateTime until);

    @Query("""
        SELECT COALESCE(SUM(o.totalCost), 0)
        FROM Order o
        WHERE o.seller = :seller
          AND o.createdAt >= :from
          AND o.createdAt <  :until
    """)
    BigDecimal getCogsBetween(@Param("seller") Seller seller,
                              @Param("from") java.time.LocalDateTime from,
                              @Param("until") java.time.LocalDateTime until);

    @Query("""
        SELECT COALESCE(SUM(o.profit), 0)
        FROM Order o
        WHERE o.seller = :seller
          AND o.createdAt >= :from
          AND o.createdAt <  :until
    """)
    BigDecimal getProfitBetween(@Param("seller") Seller seller,
                                @Param("from") java.time.LocalDateTime from,
                                @Param("until") java.time.LocalDateTime until);

    @Query("""
        SELECT COUNT(o)
        FROM Order o
        WHERE o.seller = :seller
          AND o.createdAt >= :from
          AND o.createdAt <  :until
    """)
    long getOrderCountBetween(@Param("seller") Seller seller,
                              @Param("from") java.time.LocalDateTime from,
                              @Param("until") java.time.LocalDateTime until);

    /** Top products by profit contribution for the seller-month. Result size
     *  is bounded by the caller's {@link org.springframework.data.domain.Pageable}. */
    @Query("""
        SELECT new com.profitsaathi.seller.order.TopProductSummary(
            o.product.id, o.product.name,
            COALESCE(SUM(o.unitPrice * o.quantity), 0),
            COALESCE(SUM(o.profit), 0),
            COALESCE(SUM(o.quantity), 0))
        FROM Order o
        WHERE o.seller = :seller
          AND MONTH(o.createdAt) = :month
          AND YEAR(o.createdAt) = :year
        GROUP BY o.product.id, o.product.name
        ORDER BY SUM(o.profit) DESC
    """)
    java.util.List<TopProductSummary> topProductsForMonth(
            @Param("seller") Seller seller,
            @Param("month") int month,
            @Param("year") int year,
            org.springframework.data.domain.Pageable pageable);

    /** Top products by profit contribution between two timestamps. */
    @Query("""
        SELECT new com.profitsaathi.seller.order.TopProductSummary(
            o.product.id, o.product.name,
            COALESCE(SUM(o.unitPrice * o.quantity), 0),
            COALESCE(SUM(o.profit), 0),
            COALESCE(SUM(o.quantity), 0))
        FROM Order o
        WHERE o.seller = :seller
          AND o.createdAt >= :from
          AND o.createdAt <  :until
        GROUP BY o.product.id, o.product.name
        ORDER BY SUM(o.profit) DESC
    """)
    java.util.List<TopProductSummary> topProductsBetween(
            @Param("seller") Seller seller,
            @Param("from") java.time.LocalDateTime from,
            @Param("until") java.time.LocalDateTime until,
            org.springframework.data.domain.Pageable pageable);

    @Query("""
        SELECT COUNT(o) > 0
        FROM Order o
        WHERE o.seller = :seller
          AND MONTH(o.createdAt) = :month
          AND YEAR(o.createdAt) = :year
    """)
    boolean existsSalesForMonth(@Param("seller") Seller seller,
                                @Param("month") int month,
                                @Param("year") int year);

    Page<Order> findBySeller(Seller seller, Pageable pageable);

    Optional<Order> findByOrderNo(String orderNo);
    Optional<Order> findByOrderNoIgnoreCase(String orderNo);
    Optional<Order> findByPublicToken(String publicToken);

    /** Single-query ownership-scoped lookup. */
    Optional<Order> findByIdAndSeller_Id(Long id, Long sellerId);

    Page<Order> findBySellerAndStatus(Seller seller, String status, Pageable pageable);
    Page<Order> findBySellerAndProductId(Seller seller, Long productId, Pageable pageable);
    Page<Order> findBySellerAndStatusAndProductId(Seller seller, String status, Long productId, Pageable pageable);

    @Query("""
        SELECT o FROM Order o
        WHERE o.seller = :seller
          AND (:status = '' OR o.orderStatus = :status OR o.paymentStatus = :status)
          AND (:productId IS NULL OR o.product.id = :productId)
          AND (:search = ''
               OR LOWER(o.customerName) LIKE LOWER(CONCAT('%', :search, '%'))
               OR LOWER(o.orderNo)      LIKE LOWER(CONCAT('%', :search, '%'))
               OR LOWER(o.phoneNumber)  LIKE LOWER(CONCAT('%', :search, '%')))
    """)
    Page<Order> searchOwnerOrders(@Param("seller") Seller seller,
                                  @Param("status") String status,
                                  @Param("productId") Long productId,
                                  @Param("search") String search,
                                  Pageable pageable);

    Page<Order> findByPhoneNumberAndStatusAndProductId(String number, String status, Long productId, Pageable pageable);
    Page<Order> findByPhoneNumberAndStatus(String phoneNumber, String status, Pageable pageable);
    Page<Order> findByPhoneNumberAndProductId(String phoneNumber, Long productId, Pageable pageable);
    Page<Order> findByPhoneNumber(String phoneNumber, Pageable pageable);

    // ── Aggregations used by the admin "seller report" view ──────────────
    long countBySeller_Id(Long sellerId);

    @Query("SELECT COALESCE(SUM(o.totalCost), 0) FROM Order o WHERE o.seller.id = :sellerId")
    BigDecimal sumTotalCostBySeller(@Param("sellerId") Long sellerId);

    @Query("SELECT MAX(o.createdAt) FROM Order o WHERE o.seller.id = :sellerId")
    java.time.LocalDateTime lastOrderAtBySeller(@Param("sellerId") Long sellerId);

    @Query("""
        SELECT o.orderStatus AS status, COUNT(o) AS count
        FROM Order o
        WHERE o.seller.id = :sellerId
        GROUP BY o.orderStatus
    """)
    java.util.List<OrderStatusCount> countByOrderStatusForSeller(@Param("sellerId") Long sellerId);

    @Query("""
        SELECT o.paymentStatus AS status, COUNT(o) AS count
        FROM Order o
        WHERE o.seller.id = :sellerId
        GROUP BY o.paymentStatus
    """)
    java.util.List<OrderStatusCount> countByPaymentStatusForSeller(@Param("sellerId") Long sellerId);

    /** Projection — JPA fills via Spring Data interface-projection. */
    interface OrderStatusCount {
        String getStatus();
        Long getCount();
    }

    // ── Admin dashboard queries ──────────────────────────────────────────

    long countByCreatedAtAfter(java.time.LocalDateTime since);

    @Query("SELECT COALESCE(SUM(o.totalCost), 0) FROM Order o WHERE o.createdAt >= :since")
    java.math.BigDecimal sumTotalCostSince(@Param("since") java.time.LocalDateTime since);

    /**
     * Daily GMV totals since the given timestamp. Native query because
     * {@code date_trunc} is Postgres-specific. Returns one row per day that
     * had at least one order — gaps are filled in Java by the caller.
     */
    @Query(value = """
        SELECT date_trunc('day', created_at)::date AS day,
               COALESCE(SUM(total_cost), 0)        AS value
        FROM orders
        WHERE created_at >= :since
        GROUP BY day
        ORDER BY day
    """, nativeQuery = true)
    java.util.List<DailyGmvRow> dailyGmvSince(@Param("since") java.time.LocalDateTime since);

    interface DailyGmvRow {
        java.sql.Date getDay();
        java.math.BigDecimal getValue();
    }

    /**
     * Top N sellers by GMV in the given window. JPQL group-by; the limit is
     * applied via Pageable (Spring Data's standard idiom for "top N").
     */
    @Query("""
        SELECT o.seller.id            AS sellerId,
               COUNT(o)                AS orderCount,
               COALESCE(SUM(o.totalCost), 0) AS gmv
        FROM Order o
        WHERE o.createdAt >= :since
        GROUP BY o.seller.id
        ORDER BY SUM(o.totalCost) DESC
    """)
    java.util.List<SellerGmvRow> topSellersByGmvSince(@Param("since") java.time.LocalDateTime since,
                                                       org.springframework.data.domain.Pageable pageable);

    interface SellerGmvRow {
        Long getSellerId();
        Long getOrderCount();
        java.math.BigDecimal getGmv();
    }
}
