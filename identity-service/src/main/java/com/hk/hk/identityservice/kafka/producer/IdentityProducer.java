package com.hk-fintech.hk.identityservice.kafka.producer;

import com.hk-fintech.hk.common.events.kafka.UserCreatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class IdentityProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void sendUserCreatedEvent(UserCreatedEvent event) {
        try {
            log.info("📤 Kafka'ya UserCreatedEvent gönderiliyor: {}", event.getEmail());
            kafkaTemplate.send("user-created-topic", event);
            log.info("✅ Event başarıyla gönderildi. User ID: {}", event.getUserId());
        } catch (Exception e) {
            log.error("💥 Kafka mesaj gönderme hatası! User ID: {}", event.getUserId(), e);
            // İstersen burada özel bir Exception fırlatabilirsin
        }
    }
}