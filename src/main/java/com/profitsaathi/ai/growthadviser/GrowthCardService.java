package com.profitsaathi.ai.growthadviser;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Read-side and lightweight write-side helpers for cards. Sync orchestration
 * lives in {@link GrowthCardSyncService} — keep this thin so the dashboard
 * GET path doesn't pull the LLM dependencies.
 */
@Service
@RequiredArgsConstructor
public class GrowthCardService {

    private final GrowthCardRepository cardRepository;
    private final GrowthSyncStateRepository syncStateRepository;

    @Transactional(readOnly = true)
    public List<GrowthCardDto> listForSeller(Long sellerId, GrowthCard.Status status) {
        GrowthCard.Status effective = status == null ? GrowthCard.Status.ACTIVE : status;
        return cardRepository
                .findBySellerIdAndStatusOrderByPriorityAscCreatedAtDesc(sellerId, effective)
                .stream()
                .map(GrowthCardDto::of)
                .toList();
    }

    /**
     * Update a card's status (READ / DONE / DISMISSED). Throws when the
     * card belongs to a different seller — never leak cards across
     * tenants.
     */
    @Transactional
    public GrowthCardDto updateStatus(Long sellerId, Long cardId, GrowthCard.Status status) {
        GrowthCard card = cardRepository.findById(cardId)
                .orElseThrow(() -> new EntityNotFoundException("Card not found: " + cardId));
        if (!card.getSellerId().equals(sellerId)) {
            throw new EntityNotFoundException("Card not found: " + cardId);
        }
        if (status == GrowthCard.Status.STALE || status == GrowthCard.Status.ACTIVE) {
            throw new IllegalArgumentException("Status not assignable by client: " + status);
        }
        card.setStatus(status);
        return GrowthCardDto.of(cardRepository.save(card));
    }

    /**
     * Snapshot of the seller's sync state — last sync time, today's count,
     * and (for the UI's "Sync available in 4h" hint) seconds until next
     * sync becomes allowed.
     */
    public record SyncStatus(
            LocalDateTime lastSyncAt,
            String lastSource,
            int syncsToday,
            long cooldownSecondsRemaining,
            int cooldownHours
    ) {}

    @Transactional(readOnly = true)
    public SyncStatus statusFor(Long sellerId, int cooldownHours) {
        Optional<GrowthSyncState> opt = syncStateRepository.findById(sellerId);
        if (opt.isEmpty()) {
            return new SyncStatus(null, null, 0, 0L, cooldownHours);
        }
        GrowthSyncState s = opt.get();
        long remaining = 0;
        if (s.getLastSyncAt() != null) {
            Duration elapsed = Duration.between(s.getLastSyncAt(), LocalDateTime.now());
            Duration cooldown = Duration.ofHours(cooldownHours);
            if (elapsed.compareTo(cooldown) < 0) {
                remaining = cooldown.minus(elapsed).getSeconds();
            }
        }
        return new SyncStatus(s.getLastSyncAt(), s.getLastSource(), s.getSyncsToday(), remaining, cooldownHours);
    }
}
