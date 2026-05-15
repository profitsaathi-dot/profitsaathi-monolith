package com.profitsaathi.seller.order;

import com.profitsaathi.notification.whatsapp.WhatsAppMessage;
import com.profitsaathi.notification.whatsapp.WhatsAppService;
import com.profitsaathi.seller.user.Seller;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Customer-facing notifications for order lifecycle events.
 *
 * Calls {@link WhatsAppService#send} directly — no Kafka. WhatsAppService is
 * itself {@code @Async} + {@code @Retryable}, so the calling order/refund
 * transaction returns instantly and a WAHA outage can never roll it back.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderNotificationService {

    private final WhatsAppService whatsAppService;

    @Value("${profitsaathi.public-api-url:}")
    private String publicApiUrl;

    /**
     * Notify the customer that their order has been cancelled. Composes a
     * tracking/refund URL from the seller's publicToken + the order number
     * and hands the message to the in-process WhatsAppService.
     */
    public void notifyOrderCancelled(Order order) {
        try {
            if (order == null) return;
            if (order.getPhoneNumber() == null || order.getPhoneNumber().isBlank()) {
                log.info("Skip cancel notification — no customer phone for order {}",
                        order.getOrderNo());
                return;
            }
            Seller seller = order.getSeller();
            if (seller == null) {
                log.info("Skip cancel notification — order {} has no seller", order.getOrderNo());
                return;
            }
            String sellerToken = seller.getPublicToken();
            if (sellerToken == null || sellerToken.isBlank()) {
                log.info("Skip cancel notification — seller missing publicToken for order {}",
                        order.getOrderNo());
                return;
            }

            String base = (publicApiUrl == null || publicApiUrl.isBlank())
                    ? ""
                    : publicApiUrl.replaceAll("/+$", "");
            String trackingUrl = base + "/user/" + sellerToken
                    + "/track?orderNo=" + order.getOrderNo();
            String message = "Hello — your order has been cancelled. "
                    + "Use this link to track the refund: " + trackingUrl;

            log.info("Sending cancel notification for order {} to {}",
                    order.getOrderNo(), order.getPhoneNumber());

            whatsAppService.send(WhatsAppMessage.builder()
                    .chatId(toChatId(order.getPhoneNumber()))
                    .text(message)
                    .build());
        } catch (Exception e) {
            // Never let a notification failure roll back the order/refund tx.
            log.warn("Failed to dispatch cancel notification for order {}: {}",
                    order != null ? order.getOrderNo() : "?", e.getMessage());
        }
    }

    private static String toChatId(String raw) {
        if (raw == null) return null;
        String digits = raw.replaceAll("\\D", "");
        return digits + "@c.us";
    }
}
