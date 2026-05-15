package com.profitsaathi.seller.ai;

import com.profitsaathi.seller.sales.SalesSummary;
import com.profitsaathi.seller.sales.SalesSummaryRepository;
import com.profitsaathi.seller.user.Seller;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;

@Service
@RequiredArgsConstructor
public class GrowthSuggestionService {

    private final AIOrchestratorService aiOrchestratorService;
    private final AiSuggestionRepository repository;
    private final SalesSummaryRepository salesSummaryRepository;

    public void generateGrowthSuggestions(Seller seller, SalesSummary summary) {
        if (summary == null || summary.getProfitMargin() == null) return;

        BigDecimal currentMargin = summary.getProfitMargin();

        boolean alreadyToday = repository.existsBySellerAndSuggestionTypeAndCreatedAtAfter(
                seller, "GROWTH", LocalDate.now().atStartOfDay());
        if (alreadyToday) return;

        if (currentMargin.compareTo(BigDecimal.valueOf(25)) > 0) {
            saveStaticSuggestion(seller,
                    "Business Healthy",
                    "Your margin is above 25%. Maintain current pricing strategy.",
                    "LOW", 95);
            return;
        }

        SalesSummary previous = salesSummaryRepository.findPreviousMonth(seller);
        boolean shouldCallAI;
        if (previous != null && previous.getProfitMargin() != null) {
            BigDecimal diff = currentMargin.subtract(previous.getProfitMargin()).abs();
            shouldCallAI = diff.compareTo(BigDecimal.valueOf(5)) > 0;
        } else {
            shouldCallAI = true;
        }
        if (!shouldCallAI) return;

        String festival = detectFestival();
        String prompt = """
                You are a small business growth advisor in India.

                Current Season: %s

                Business Data:
                - Total Sales: %s
                - Total Expenses: %s
                - Net Profit: %s
                - Profit Margin: %s%%
                - Health Score: %s/100

                Give exactly 5 short action bullet suggestions.
                Keep it practical and simple.
                """.formatted(
                festival,
                summary.getTotalSales(),
                summary.getTotalExpenses(),
                summary.getNetProfit(),
                summary.getProfitMargin(),
                summary.getHealthScore());

        String aiResponse = aiOrchestratorService.generateAIResponse(prompt);

        Arrays.stream(aiResponse.split("\n"))
                .filter(line -> !line.trim().isEmpty())
                .limit(5)
                .forEach(line -> {
                    AiSuggestion s = new AiSuggestion();
                    s.setSeller(seller);
                    s.setSuggestionType("GROWTH");
                    s.setTitle("Business Improvement Tip");
                    s.setDescription(line.trim());
                    s.setPriority("MEDIUM");
                    s.setConfidenceScore(calculateConfidence(summary));
                    s.setStatus("ACTIVE");
                    s.setReadStatus("UNREAD");
                    s.setCreatedAt(LocalDateTime.now());
                    repository.save(s);
                });
    }

    private void saveStaticSuggestion(Seller seller, String title, String description,
                                      String priority, int confidence) {
        AiSuggestion s = new AiSuggestion();
        s.setSeller(seller);
        s.setSuggestionType("GROWTH");
        s.setTitle(title);
        s.setDescription(description);
        s.setPriority(priority);
        s.setConfidenceScore(confidence);
        s.setStatus("ACTIVE");
        s.setReadStatus("UNREAD");
        s.setCreatedAt(LocalDateTime.now());
        repository.save(s);
    }

    private String detectFestival() {
        return switch (LocalDate.now().getMonthValue()) {
            case 1 -> "Makar Sankranti / Republic Day";
            case 3 -> "Holi Season";
            case 8 -> "Raksha Bandhan";
            case 10 -> "Diwali Season";
            case 12 -> "Christmas / New Year";
            default -> "Normal Business Month";
        };
    }

    private int calculateConfidence(SalesSummary summary) {
        int score = 50;
        if (summary.getProfitMargin().compareTo(BigDecimal.valueOf(20)) > 0) score += 20;
        if (summary.getHealthScore() != null && summary.getHealthScore() > 70) score += 10;
        if (summary.getTotalSales().compareTo(BigDecimal.valueOf(100000)) > 0) score += 10;
        return Math.min(score, 95);
    }
}
