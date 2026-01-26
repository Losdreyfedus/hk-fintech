package com.hk-fintech.hk.walletservice.kafka.consumer;

import com.hk-fintech.hk.common.events.kafka.UserCreatedEvent;
import com.hk-fintech.hk.walletservice.kafka.producer.WalletProducer;
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
    private final WalletProducer walletProducer;

    @KafkaListener(topics = "user-created-topic", groupId = "wallet-group")
    public void consumeUserCreatedEvent(UserCreatedEvent event) {
        log.info("📩 Kafka'dan UserCreatedEvent geldi. User ID: {}", event.getUserId());

        try {
            walletService.createWallet(event.getUserId());

        } catch (Exception e) {
            log.error("💥 Cüzdan oluşturulurken hata çıktı! User ID: {}", event.getUserId(), e);

            walletProducer.sendWalletCreationFailedEvent(event.getUserId());
        }
    }
}