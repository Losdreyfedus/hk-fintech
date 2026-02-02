package com.hk-fintech.hk.walletservice.kafka.consumer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hk-fintech.hk.common.events.kafka.UserCreatedEvent;
import com.hk-fintech.hk.walletservice.service.InboxService;
import com.hk-fintech.hk.walletservice.service.WalletService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class WalletConsumer {

    private final WalletService walletService;
    private final InboxService inboxService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "user-created-topic", groupId = "wallet-group-v1")
    public void consumeUserCreatedEvent(String message) {
        log.info("📩 Kafka'dan mesaj alındı: {}", message);

        try {
            UserCreatedEvent event = objectMapper.readValue(message, UserCreatedEvent.class);
            String idempotencyKey = "CREATE_WALLET_" + event.getUserId();
            inboxService.processIdempotent(idempotencyKey, "CREATE_WALLET", () -> {

                log.info("🚀 Cüzdan oluşturma işlemi başlatılıyor. User ID: {}", event.getUserId());
                walletService.createWallet(event.getUserId());

            });

        } catch (JsonProcessingException e) {
            log.error("💥 JSON Çevirme Hatası! Mesaj formatı bozuk: {}", message, e);

        } catch (Exception e) {
            log.error("💥 Beklenmeyen hata oluştu!", e);

        }
    }
}