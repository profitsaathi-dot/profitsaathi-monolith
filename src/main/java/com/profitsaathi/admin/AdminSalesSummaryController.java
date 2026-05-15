package com.profitsaathi.admin;

import com.profitsaathi.seller.order.OrderRepository;
import com.profitsaathi.seller.sales.SalesSummary;
import com.profitsaathi.seller.sales.SalesSummaryRepository;
import com.profitsaathi.seller.sales.SalesSummaryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * One-shot admin tooling for the SalesSummary table.
 *
 * <p>Historical rows are wrong because the pre-fix
 * {@code OrderRepository.getTotalSalesForMonth} summed {@code totalCost}
 * (which {@code OrderService} sets to {@code costPrice × quantity}, i.e.
 * COGS). Every existing row therefore has {@code netProfit = 0},
 * {@code profitMargin = 0}, {@code healthScore = 30}. Trigger this endpoint
 * once after deploying the OrderRepository fix to overwrite them with the
 * correct numbers, leaving each row's primary key intact.
 */
@RestController
@RequestMapping("/api/v1/admin/sales-summary")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Slf4j
public class AdminSalesSummaryController {

    private final SalesSummaryRepository repository;
    private final SalesSummaryService service;
    private final OrderRepository orderRepository;

    @PostMapping("/recompute")
    public Map<String, Object> recompute() {
        log.info("Admin-triggered SalesSummary recompute starting…");

        List<SalesSummary> rows = repository.findAll();
        int updated = 0, unchanged = 0, skipped = 0, failed = 0;

        for (SalesSummary row : rows) {
            try {
                if (row.getSeller() == null
                        || row.getMonth() == null
                        || row.getYear() == null) {
                    skipped++;
                    continue;
                }

                BigDecimal revenue = orderRepository.getRevenueForMonth(
                        row.getSeller(), row.getMonth(), row.getYear());
                BigDecimal cogs = orderRepository.getTotalExpensesForMonth(
                        row.getSeller(), row.getMonth(), row.getYear());

                if (revenue == null) revenue = BigDecimal.ZERO;
                if (cogs == null) cogs = BigDecimal.ZERO;

                boolean drift = row.getTotalSales() == null
                        || revenue.compareTo(row.getTotalSales()) != 0
                        || cogs.compareTo(
                                row.getTotalExpenses() == null
                                        ? BigDecimal.ZERO : row.getTotalExpenses()) != 0;
                if (!drift) {
                    unchanged++;
                    continue;
                }

                service.recomputeMonthlySummary(
                        row.getSeller(), row.getMonth(), row.getYear(), revenue, cogs);
                updated++;
            } catch (Exception e) {
                log.warn("Recompute failed for SalesSummary id={}: {}",
                        row.getId(), e.getMessage());
                failed++;
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalRows", rows.size());
        result.put("updated", updated);
        result.put("unchanged", unchanged);
        result.put("skipped", skipped);
        result.put("failed", failed);
        log.info("SalesSummary recompute done: {}", result);
        return result;
    }
}
