package com.hk-fintech.hk.walletservice.kafka.producer;

import com.hk-fintech.hk.common.events.kafka.WalletCreationFailedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class WalletProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void sendWalletCreationFailedEvent(Integer userId) {
        try {
            WalletCreationFailedEvent event = new WalletCreationFailedEvent(userId);

            log.warn("⚠️ Rollback Event (WalletCreationFailed) hazırlanıyor... User ID: {}", userId);
            kafkaTemplate.send("wallet-failed-topic", event);
            log.info("❌ Rollback Event başarıyla gönderildi. User ID: {}", userId);

        } catch (Exception e) {
            log.error("💥 Rollback eventi bile gönderilemedi! Çok kritik durum. User ID: {}", userId, e);
        }
    }
}