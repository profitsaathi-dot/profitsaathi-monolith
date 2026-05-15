package com.profitsaathi.seller.smartPricing.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.profitsaathi.Geo.DTO.LocationResponse;
import com.profitsaathi.Geo.service.GeoCodeService;
import com.profitsaathi.auth.AuthenticatedPrincipal;
import com.profitsaathi.seller.aichat.AiChatProviderRouter;
import com.profitsaathi.seller.aichat.ChatTurn;
import com.profitsaathi.seller.smartPricing.DTO.PricingRequest;
import com.profitsaathi.seller.smartPricing.DTO.PricingResponse;
import com.profitsaathi.seller.smartPricing.ai.AiTextProviderRouter;
import com.profitsaathi.seller.user.Seller;
import com.profitsaathi.seller.user.SellerRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;

@Service
@AllArgsConstructor
public class PricingService {

    private final SellerRepository sellerRepository;
    private final AiTextProviderRouter aiTextProviderRouter;
    private final GeoCodeService geoCodeService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public PricingResponse calculate(
            AuthenticatedPrincipal me,
            PricingRequest req
    ) {

        // Safe null handling
        double shipping =
                req.shippingCost() != null
                        ? req.shippingCost()
                        : 0;

        double packaging =
                req.packagingCost() != null
                        ? req.packagingCost()
                        : 0;

        // Total Cost
        double totalCost =
                req.costPrice()
                        + shipping
                        + packaging;

        // Break-even only
        double breakEven = totalCost;



        LocationResponse location=new LocationResponse("","","","","");
        if (req.lat() != null && req.lng() != null
                && req.lat() != 0.0 && req.lng() != 0.0) {

            location = geoCodeService.getLocation(req.lat(), req.lng());
            //System.out.println("Location datils "+location.city()+""+location.state());
        }




        // AI Prompt
        // AI Prompt
        String aiPrompt = String.format(Locale.ENGLISH, """
        You are a pricing strategist for Indian D2C sellers.
        
        TUNE PRICING AND STRATEGY FOR THIS LOCATION: 
        %s (City: %s, State: %s)
        Analyze local purchasing power, regional demand, and competition for this specific area.

        PRODUCT:
        - Name: %s
        - Description: %s
        - Selling Method: %s

        COSTS:
        - Total Landed Cost: ₹%.2f
        - Competitor Price: %s

        TASK:
        RETURN MINIFIED JSON ONLY. NO MARKDOWN. 
        STRINGS MUST BE EXTREMELY SHORT (MAX 5 WORDS).

        {
          "recommendedPrice": number,
          "aggressivePrice": number,
          "safePrice": number,
          "premiumPrice": number,
          "expectedMarginPercent": number,
          "localMarket": "string",
          "demandLevel": "LOW | MEDIUM | HIGH",
          "festivalPotential": "LOW | MEDIUM | HIGH",
          "competitorDensity": "LOW | MEDIUM | HIGH",
          "strategy": "string",
          "warning": "string",
          "sellingTip": "string"
        }
        """,
                location.displayName(),
                location.city(),
                location.state(),

                req.productName(),
                req.description(),
                req.sellingMethod(),

                totalCost,
                req.competitorPrice() != null ? "₹" + req.competitorPrice() : "Unknown"
        );

        //System.out.println("Location datils "+location.city()+""+location.state());

        // Get Seller
        Seller seller = sellerRepository.findById(me.subjectId())
                .orElseThrow(() ->
                        new EntityNotFoundException("Seller not found"));

        // AI Message
        ChatTurn currentTurn = ChatTurn.user(aiPrompt);

        // Empty history
        List<ChatTurn> history = List.of();

        // Call AI
        AiTextProviderRouter.Result result =
                aiTextProviderRouter.ask(
                        seller,
                        aiPrompt
                );

        String aiResponse = result.reply();

        System.out.println("RAW AI RESPONSE:");
        System.out.println(aiResponse);

        if (!aiResponse.contains("{")) {
            throw new RuntimeException(
                    "Invalid AI response"
            );
        }

        try {

            // Parse JSON
            String cleanedJson = extractJson(aiResponse);
            System.out.println("CLEANED JSON:");
            System.out.println(cleanedJson);

            JsonNode json =
                    objectMapper.readTree(cleanedJson);

            // AI Prices
            // AI Prices
            double aggressivePrice = json.path("aggressivePrice").asDouble(totalCost * 1.2);
            double safePrice = json.path("safePrice").asDouble(totalCost * 1.4);
            double premiumPrice = json.path("premiumPrice").asDouble(totalCost * 1.8);
            double recommendedPrice = json.path("recommendedPrice").asDouble(safePrice);
            int expectedMargin = json.path("expectedMarginPercent").asInt(30);

            // AI Insights (Short strings)
            String strategy = json.path("strategy").asText("Good pricing strategy.");
            String warning = json.path("warning").asText("");
            String sellingTip = json.path("sellingTip").asText("");
            String localMarket = json.path("localMarket").asText("");
            String demandLevel = json.path("demandLevel").asText("");
            String festivalPotential = json.path("festivalPotential").asText("");
            String competitorDensity = json.path("competitorDensity").asText("");

            // Final Response
            return new PricingResponse(
                    totalCost,
                    breakEven,
                    aggressivePrice,
                    safePrice,
                    premiumPrice,
                    new PricingResponse.Margins(
                            0,
                            expectedMargin,
                            expectedMargin,
                            expectedMargin
                    ),
                    recommendedPrice,
                    strategy + " Tip: " + sellingTip, // Merged to save a DTO field
                    warning,

                    // From Java GeoCodeService
                    location.displayName(),
                    location.city(),
                    location.state(),

                    // From AI
                    localMarket,
                    demandLevel,
                    festivalPotential,
                    competitorDensity
            );


        } catch (Exception e) {

            e.printStackTrace();

            // Fallback if AI JSON fails
            return new PricingResponse(
                    totalCost,
                    breakEven,
                    totalCost * 1.2,
                    totalCost * 1.4,
                    totalCost * 1.8,
                    new PricingResponse.Margins(
                            0,
                            20,
                            35,
                            50
                    ),
                    totalCost * 1.4,
                    "AI pricing analysis unavailable currently.", // Merged to save a DTO field
                    "Unable to parse AI response.",

                    // From Java GeoCodeService
                    location.displayName(),
                    location.city(),
                    location.state(),

                    // From AI
                    "",
                    "",
                    "",
                    ""
            );

        }
    }

    private String extractJson(String text) {

        if (text == null || text.isBlank()) {
            return "{}";
        }

        // Remove markdown wrappers
        text = text.replace("```json", "")
                .replace("```", "")
                .trim();

        int start = text.indexOf("{");
        int end = text.lastIndexOf("}");

        // Proper JSON
        if (start >= 0 && end > start) {
            return text.substring(start, end + 1);
        }

        // Partial JSON recovery
        if (start >= 0) {

            String partial = text.substring(start);

            long openBraces = partial.chars()
                    .filter(ch -> ch == '{')
                    .count();

            long closeBraces = partial.chars()
                    .filter(ch -> ch == '}')
                    .count();

            StringBuilder fixed = new StringBuilder(partial);

            while (closeBraces < openBraces) {
                fixed.append("}");
                closeBraces++;
            }

            return fixed.toString();
        }

        return "{}";
    }
}