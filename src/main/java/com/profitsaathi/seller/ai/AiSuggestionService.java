package com.profitsaathi.seller.ai;

import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@AllArgsConstructor
public class AiSuggestionService {

    private final AiSuggestionRepository repository;

    @Transactional
    public AiSuggestion save(AiSuggestion suggestion) {
        return repository.save(suggestion);
    }

    @Transactional(readOnly = true)
    public List<AiSuggestion> getBySeller(Long sellerId) {
        return repository.findBySellerId(sellerId);
    }
}
