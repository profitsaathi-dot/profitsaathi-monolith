package com.profitsaathi.seller.pricing;

import com.profitsaathi.seller.ai.AIOrchestratorService;
import com.profitsaathi.seller.product.Product;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class PricingEngineService {

    private final PricingAnalysisRepository pricingAnalysisRepository;
    private final AIOrchestratorService aiOrchestratorService;

    @Value("${pricing.safe-margin}")
    private int safeMargin;

    @Value("${pricing.aggressive-margin}")
    private int aggressiveMargin;

    public void runAutoPricing(Product product) {
        boolean alreadyCalculatedToday = pricingAnalysisRepository
                .existsByProductIdAndLastCalculatedAtAfter(
                        product.getId(),
                        LocalDate.now().atStartOfDay());
        if (alreadyCalculatedToday) return;

        BigDecimal costPrice = safe(product.getCostPrice());
        BigDecimal shipping = safe(product.getShippingCost());
        BigDecimal packaging = safe(product.getPackagingCost());
        BigDecimal competitorPrice = product.getCompetitorPrice();

        BigDecimal totalCost = costPrice.add(shipping).add(packaging);
        if (totalCost.compareTo(BigDecimal.ZERO) <= 0) return;

        int finalMargin = decideMargin(totalCost, competitorPrice);

        BigDecimal safePrice = totalCost.multiply(BigDecimal.valueOf(1 + safeMargin / 100.0));
        BigDecimal aggressivePrice = totalCost.multiply(BigDecimal.valueOf(1 + aggressiveMargin / 100.0));
        BigDecimal suggestedPrice = totalCost.multiply(BigDecimal.valueOf(1 + finalMargin / 100.0));

        BigDecimal profitMargin = suggestedPrice
                .subtract(totalCost)
                .divide(suggestedPrice, 2, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));

        PricingAnalysis analysis = pricingAnalysisRepository.findByProductId(product.getId())
                .orElse(new PricingAnalysis());

        if (analysis.getSuggestedPrice() != null) {
            BigDecimal diff = suggestedPrice.subtract(analysis.getSuggestedPrice()).abs();
            if (diff.compareTo(BigDecimal.valueOf(5)) < 0) {
                analysis.setLastCalculatedAt(LocalDateTime.now());
                pricingAnalysisRepository.save(analysis);
                return;
            }
        }

        analysis.setProduct(product);
        analysis.setTotalCost(totalCost);
        analysis.setBreakEvenPrice(totalCost);
        analysis.setSafePrice(safePrice);
        analysis.setAggressivePrice(aggressivePrice);
        analysis.setSuggestedPrice(suggestedPrice);
        analysis.setProfitMargin(profitMargin);
        analysis.setAnalysisType("AUTO");
        analysis.setLastCalculatedAt(LocalDateTime.now());

        try {
            String prompt = buildPrompt(totalCost, safePrice, aggressivePrice,
                    suggestedPrice, profitMargin, competitorPrice);
            analysis.setAiSummary(aiOrchestratorService.generateAIResponse(prompt));
        } catch (Exception e) {
            analysis.setAiSummary("AI summary unavailable.");
        }

        pricingAnalysisRepository.save(analysis);
    }

    private BigDecimal safe(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }

    private int decideMargin(BigDecimal totalCost, BigDecimal competitorPrice) {
        if (competitorPrice == null) return safeMargin;
        BigDecimal safePrice = totalCost.multiply(BigDecimal.valueOf(1 + safeMargin / 100.0));

        if (competitorPrice.compareTo(totalCost) <= 0) return safeMargin;
        if (competitorPrice.compareTo(safePrice.multiply(BigDecimal.valueOf(1.5))) > 0) {
            // "Competitor priced way above safe price" bonus, capped so a high
            // safeMargin config doesn't push us into absurd territory.
            int boosted = safeMargin + 5;
            int ceiling = Math.max(safeMargin, aggressiveMargin) + 10;
            return Math.min(boosted, ceiling);
        }
        if (competitorPrice.compareTo(safePrice) < 0) return aggressiveMargin;
        return safeMargin;
    }

    private String buildPrompt(BigDecimal totalCost,
                               BigDecimal safePrice,
                               BigDecimal aggressivePrice,
                               BigDecimal suggestedPrice,
                               BigDecimal profitMargin,
                               BigDecimal competitorPrice) {
        return """
                You are an Indian small business pricing advisor.

                All prices in INR.
                Give 4 short practical lines only.

                Cost: %s
                Safe Price: %s
                Competitive Price: %s
                Suggested Price: %s
                Margin: %s%%
                Competitor: %s
                """.formatted(totalCost, safePrice, aggressivePrice,
                              suggestedPrice, profitMargin, competitorPrice);
    }
}
