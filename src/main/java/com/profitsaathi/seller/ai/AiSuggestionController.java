package com.profitsaathi.seller.ai;

import com.profitsaathi.auth.AuthenticatedPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/ai-suggestions")
public class AiSuggestionController {

    private final AiSuggestionService service;

    public AiSuggestionController(AiSuggestionService service) {
        this.service = service;
    }

    @PostMapping
    public AiSuggestion create(@RequestBody AiSuggestion suggestion) {
        return service.save(suggestion);
    }

    @GetMapping("/me")
    public List<AiSuggestion> mine(@AuthenticationPrincipal AuthenticatedPrincipal me) {
        return service.getBySeller(me.subjectId());
    }

    @GetMapping("/seller/{sellerId}")
    public List<AiSuggestion> getBySeller(@PathVariable Long sellerId) {
        return service.getBySeller(sellerId);
    }
}
