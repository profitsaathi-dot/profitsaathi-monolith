package com.profitsaathi.ai.log;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public interface AiChatLogRepository extends JpaRepository<AiChatLog, Long> {

    Page<AiChatLog> findAllByOrderByCreatedAtDesc(Pageable pageable);

    Page<AiChatLog> findByPrincipalEmailOrderByCreatedAtDesc(String email, Pageable pageable);

    long countByCreatedAtAfter(LocalDateTime since);

    @Query("SELECT COALESCE(SUM(l.cost), 0) FROM AiChatLog l WHERE l.createdAt >= :since")
    BigDecimal sumCostSince(@Param("since") LocalDateTime since);
}
