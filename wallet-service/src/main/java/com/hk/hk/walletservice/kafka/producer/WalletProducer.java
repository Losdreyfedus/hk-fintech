package com.hk-fintech.hk.walletservice.kafka.producer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hk-fintech.hk.common.events.kafka.WalletCreationFailedEvent;
import com.hk-fintech.hk.walletservice.entity.OutboxMessage; // Wallet paketinden import et
import com.hk-fintech.hk.walletservice.entity.OutboxStatus;
import com.hk-fintech.hk.walletservice.repository.OutboxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional; // Önemli

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
@Slf4j
public class WalletProducer {

    private final OutboxRepository outboxRepository; // KafkaTemplate yerine DB
    private final ObjectMapper objectMapper;
    @Transactional
    public void sendWalletCreationFailedEvent(Long userId) {
        try {
            WalletCreationFailedEvent event = new WalletCreationFailedEvent(userId);
            String payload = objectMapper.writeValueAsString(event);

            OutboxMessage message = OutboxMessage.builder()
                    .topic("wallet-failed-topic")
                    .payload(payload)
                    .status(OutboxStatus.PENDING)
                    .createdAt(LocalDateTime.now())
                    .build();

            outboxRepository.save(message);

            log.warn("⚠️ Rollback Event Outbox'a kaydedildi. User ID: {}", userId);

        } catch (Exception e) {

            log.error("💥 Kritik Hata! Rollback mesajı DB'ye yazılamadı. User ID: {}", userId, e);
        }
    }
}