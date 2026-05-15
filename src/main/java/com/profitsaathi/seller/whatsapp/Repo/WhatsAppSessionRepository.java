package com.profitsaathi.seller.whatsapp.Repo;

import com.profitsaathi.seller.whatsapp.Entity.WhatsAppSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface WhatsAppSessionRepository extends JpaRepository<WhatsAppSession, Long> {
    Optional<WhatsAppSession> findBySeller_Id(Long sellerId);
    Optional<WhatsAppSession> findBySessionName(String sessionName);
}
