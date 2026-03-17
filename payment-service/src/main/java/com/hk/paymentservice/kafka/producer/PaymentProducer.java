package com.hk.paymentservice.kafka.producer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hk.common.events.kafka.TopUpCompletedEvent;
import com.hk.common.events.kafka.PaymentCompletedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentProducer {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public void sendPaymentCompletedEvent(PaymentCompletedEvent event) {
        try {
            String message = objectMapper.writeValueAsString(event);
            kafkaTemplate.send("payment-completed-topic", message);
            log.info("🚀 PaymentCompletedEvent Kafka'ya gönderildi: {}", message);
        } catch (Exception e) {
            log.error("💥 PaymentCompletedEvent Kafka'ya gönderilirken hata oluştu!", e);
        }
    }

    public void sendTopUpCompletedEvent(TopUpCompletedEvent event) {
        try {
            String message = objectMapper.writeValueAsString(event);
            kafkaTemplate.send("topup-completed-topic", message);
            log.info("🚀 TopUpCompletedEvent Kafka'ya gönderildi: {}", message);
        } catch (Exception e) {
            log.error("💥 TopUpCompletedEvent Kafka'ya gönderilirken hata oluştu!", e);
        }
    }
}
