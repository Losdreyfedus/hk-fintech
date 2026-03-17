package com.hk.walletservice.kafka.consumer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hk.common.events.kafka.TopUpCompletedEvent;
import com.hk.walletservice.entity.TransactionType;
import com.hk.walletservice.entity.Wallet;
import com.hk.walletservice.repository.WalletRepository;
import com.hk.walletservice.service.InboxService;
import com.hk.walletservice.service.WalletService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class TopUpCompletedConsumer {

    private final WalletService walletService;
    private final WalletRepository walletRepository;
    private final InboxService inboxService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "topup-completed-topic", groupId = "wallet-group-v1")
    public void consumeTopUpCompletedEvent(String message) {
        log.info("📩 Kafka'dan TopUpCompletedEvent alındı: {}", message);

        try {
            TopUpCompletedEvent event = objectMapper.readValue(message, TopUpCompletedEvent.class);

            if (!"SUCCESS".equals(event.getStatus())) {
                log.info("Para yükleme başarısız (Status: {}). Bakiye artırılmayacak.", event.getStatus());
                return;
            }

            String idempotencyKey = "TOP_UP_WALLET_" + event.getPaymentId();
            inboxService.processIdempotent(idempotencyKey, "TOP_UP", () -> {

                log.info("🚀 Cüzdan bakiyesi artırılıyor. Payment ID: {}", event.getPaymentId());

                Wallet wallet = walletService.getByUserId(event.getUserId());
                wallet.setBalance(wallet.getBalance().add(event.getAmount()));
                walletRepository.save(wallet);

                log.info("✅ Bakiye artırıldı (Yeni bakiye: {}). User ID: {}", wallet.getBalance(), event.getUserId());

                walletService.addTransactionHistory(
                        wallet.getId(),
                        event.getAmount(),
                        TransactionType.TOP_UP,
                        event.getPaymentId().toString(),
                        "Cüzdana Para Yükleme (Top-Up)");

            });

        } catch (JsonProcessingException e) {
            log.error("💥 JSON Çevirme Hatası! Mesaj formatı bozuk: {}", message, e);
        } catch (Exception e) {
            log.error("💥 Beklenmeyen hata oluştu!", e);
        }
    }
}
