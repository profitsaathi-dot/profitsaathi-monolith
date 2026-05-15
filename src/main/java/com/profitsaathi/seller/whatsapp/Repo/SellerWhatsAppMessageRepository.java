package com.profitsaathi.seller.whatsapp.Repo;

import com.profitsaathi.seller.whatsapp.Entity.SellerWhatsAppMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SellerWhatsAppMessageRepository extends JpaRepository<SellerWhatsAppMessage, Long> {
}
