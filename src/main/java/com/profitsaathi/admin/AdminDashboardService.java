package com.profitsaathi.admin;

import com.profitsaathi.seller.order.OrderRepository;
import com.profitsaathi.seller.user.Seller;
import com.profitsaathi.seller.user.SellerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Aggregates the dashboard read-model. All queries are bounded by a
 * {@code windowDays} parameter (default 30). Daily GMV gaps are filled
 * with zeros so the chart line is continuous, not a sparse scatter.
 */
@Service
@RequiredArgsConstructor
public class AdminDashboardService {

    private final SellerRepository sellerRepository;
    private final OrderRepository orderRepository;

    @Transactional(readOnly = true)
    public AdminDashboard load(int windowDays) {
        int effectiveDays = windowDays <= 0 ? 30 : Math.min(windowDays, 365);
        LocalDateTime since = LocalDateTime.now().minusDays(effectiveDays);

        // ── Header totals ────────────────────────────────────────────────
        long activeSellers = sellerRepository.countByStatus(Seller.Status.ACTIVE);
        long totalSellers = sellerRepository.count();
        long newSellers = sellerRepository.countByCreatedAtAfter(since);
        long orderCount = orderRepository.countByCreatedAtAfter(since);
        BigDecimal gmv = orderRepository.sumTotalCostSince(since);
        if (gmv == null) gmv = BigDecimal.ZERO;
        BigDecimal aov = orderCount == 0
                ? BigDecimal.ZERO
                : gmv.divide(BigDecimal.valueOf(orderCount), 2, RoundingMode.HALF_UP);

        AdminDashboard.Totals totals = new AdminDashboard.Totals(
                activeSellers, totalSellers, newSellers, orderCount, gmv, aov);

        // ── Daily GMV trend (zero-fill missing days) ─────────────────────
        Map<LocalDate, BigDecimal> raw = new HashMap<>();
        for (OrderRepository.DailyGmvRow row : orderRepository.dailyGmvSince(since)) {
            if (row.getDay() != null) {
                raw.put(row.getDay().toLocalDate(),
                        row.getValue() == null ? BigDecimal.ZERO : row.getValue());
            }
        }
        Map<LocalDate, BigDecimal> filled = new TreeMap<>();
        LocalDate startDay = since.toLocalDate();
        LocalDate today = LocalDate.now();
        for (LocalDate d = startDay; !d.isAfter(today); d = d.plusDays(1)) {
            filled.put(d, raw.getOrDefault(d, BigDecimal.ZERO));
        }
        List<AdminDashboard.DailyGmv> gmvByDay = new ArrayList<>(filled.size());
        filled.forEach((d, v) -> gmvByDay.add(new AdminDashboard.DailyGmv(d, v)));

        // ── Top sellers by GMV in the window ─────────────────────────────
        List<AdminDashboard.TopSeller> topSellers = new ArrayList<>();
        List<OrderRepository.SellerGmvRow> rows =
                orderRepository.topSellersByGmvSince(since, PageRequest.of(0, 10));
        // Resolve seller info in one shot, then walk in the order returned by the
        // aggregation so the GMV ordering is preserved.
        if (!rows.isEmpty()) {
            List<Long> ids = rows.stream().map(OrderRepository.SellerGmvRow::getSellerId).toList();
            Map<Long, Seller> sellerById = new HashMap<>();
            sellerRepository.findAllById(ids).forEach(s -> sellerById.put(s.getId(), s));
            for (OrderRepository.SellerGmvRow row : rows) {
                Seller s = sellerById.get(row.getSellerId());
                topSellers.add(new AdminDashboard.TopSeller(
                        row.getSellerId(),
                        s != null ? s.getName() : "Unknown",
                        s != null ? s.getEmail() : null,
                        s != null ? s.getStoreName() : null,
                        s != null && s.getStatus() != null ? s.getStatus().name() : null,
                        row.getOrderCount() == null ? 0 : row.getOrderCount(),
                        row.getGmv() == null ? BigDecimal.ZERO : row.getGmv()
                ));
            }
        }

        return new AdminDashboard(effectiveDays, totals, gmvByDay, topSellers);
    }
}
