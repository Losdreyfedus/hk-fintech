package com.hk.walletservice.kafka.consumer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hk.common.events.kafka.PaymentCompletedEvent;
import com.hk.walletservice.entity.TransactionType;
import com.hk.walletservice.entity.Wallet;
import com.hk.walletservice.service.InboxService;
import com.hk.walletservice.service.WalletService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentCompletedConsumer {

    private final WalletService walletService;
    private final InboxService inboxService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "payment-completed-topic", groupId = "wallet-group-v1")
    public void consumePaymentCompletedEvent(String message) {
        log.info("📩 Kafka'dan PaymentCompletedEvent alındı: {}", message);

        try {
            PaymentCompletedEvent event = objectMapper.readValue(message, PaymentCompletedEvent.class);

            // Sadece başarılı ödemeler için işlem geçmişine kayıt atıyoruz
            if (!"SUCCESS".equals(event.getStatus())) {
                log.info("Ödeme başarılı değil (Status: {}). Geçmişe eklenmeyecek.", event.getStatus());
                return;
            }

            String idempotencyKey = "PAYMENT_HISTORY_" + event.getPaymentId();
            inboxService.processIdempotent(idempotencyKey, "LOG_TRANSACTION", () -> {

                log.info("🚀 İşlem geçmişi (Invoice Payment) kaydediliyor. Payment ID: {}", event.getPaymentId());

                // Kullanıcının cüzdanını buluyoruz. Ödeme geçmişini bu cüzdana bağlayacağız.
                Wallet wallet = walletService.getByUserId(event.getUserId());

                String description = "Fatura ödemesi: Fatura ID " + event.getInvoiceId();
                walletService.addTransactionHistory(
                        wallet.getId(),
                        event.getAmount().negate(), // Ödeme olduğu için bakiyeden çıkış gibi gösterilebilir eksi
                                                    // değerle (veya artı da kalabilir, domain'e göre)
                        TransactionType.INVOICE_PAYMENT,
                        event.getPaymentId().toString(),
                        description);

            });

        } catch (JsonProcessingException e) {
            log.error("💥 JSON Çevirme Hatası! Mesaj formatı bozuk: {}", message, e);
        } catch (Exception e) {
            log.error("💥 Beklenmeyen hata oluştu!", e);
        }
    }
}
