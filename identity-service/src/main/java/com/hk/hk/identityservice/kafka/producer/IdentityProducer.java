package com.hk-fintech.hk.identityservice.kafka.producer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hk-fintech.hk.common.events.kafka.UserCreatedEvent;
import com.hk-fintech.hk.identityservice.entity.OutboxMessage;
import com.hk-fintech.hk.identityservice.entity.OutboxStatus;
import com.hk-fintech.hk.identityservice.repository.OutboxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
@Slf4j
public class IdentityProducer {

    private final OutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public void scheduleUserCreatedEvent(UserCreatedEvent event) {
        try {
            String jsonPayload = objectMapper.writeValueAsString(event);

            OutboxMessage message = OutboxMessage.builder()
                    .topic("user-created-topic")
                    .payload(jsonPayload)
                    .status(OutboxStatus.PENDING)
                    .createdAt(LocalDateTime.now())
                    .build();

            outboxRepository.save(message);

            log.info("📮 Event Outbox tablosuna yazıldı. Kafka bekleniyor... User ID: {}", event.getUserId());

        } catch (JsonProcessingException e) {
            log.error("JSON çevrim hatası! Event kaydedilemedi.", e);
            throw new RuntimeException(e);
        }
    }
}