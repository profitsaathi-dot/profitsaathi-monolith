package com.profitsaathi.seller.sales;

import com.profitsaathi.seller.dashboard.DashboardSummaryDTO;
import com.profitsaathi.seller.user.Seller;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SalesSummaryRepository extends JpaRepository<SalesSummary, Long> {

    Optional<SalesSummary> findBySellerIdAndMonthAndYear(Long sellerId, Integer month, Integer year);

    /** True when an existing row for the seller-month has already been
     *  emailed — guards the monthly scheduler against re-mailing on re-runs. */
    boolean existsBySellerAndMonthAndYearAndEmailedAtIsNotNull(
            Seller seller, Integer month, Integer year);

    SalesSummary findTopBySellerOrderByYearDescMonthDesc(Seller seller);

    @Query("""
           SELECT s FROM SalesSummary s
           WHERE s.seller = :seller
           ORDER BY s.year DESC, s.month DESC
           """)
    List<SalesSummary> findAllBySellerOrderByYearDescMonthDesc(@Param("seller") Seller seller);

    boolean existsBySellerAndMonthAndYear(Seller seller, Integer month, Integer year);

    default SalesSummary findPreviousMonth(Seller seller) {
        List<SalesSummary> summaries = findAllBySellerOrderByYearDescMonthDesc(seller);
        return summaries.size() >= 2 ? summaries.get(1) : null;
    }

    @Query("""
           SELECT new com.profitsaathi.seller.dashboard.DashboardSummaryDTO(
               COALESCE(SUM(s.totalSales), 0),
               COALESCE(SUM(s.totalExpenses), 0),
               COALESCE(SUM(s.netProfit), 0),
               (SELECT COUNT(p) FROM Product p WHERE p.seller.id = :sellerId)
           )
           FROM SalesSummary s
           WHERE s.seller.id = :sellerId
           """)
    DashboardSummaryDTO getDashboardSummary(@Param("sellerId") Long sellerId);
}
