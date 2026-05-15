package com.profitsaathi.seller.aichat;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AiChatSessionRepository extends JpaRepository<AiChatSession, Long> {

    List<AiChatSession> findBySellerIdOrderByUpdatedAtDesc(Long sellerId);

    Optional<AiChatSession> findByIdAndSellerId(Long id, Long sellerId);
}
