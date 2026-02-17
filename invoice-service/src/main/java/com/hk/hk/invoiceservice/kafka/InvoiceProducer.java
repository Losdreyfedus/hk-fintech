package com.hk-fintech.hk.invoiceservice.kafka;

import com.hk-fintech.hk.invoiceservice.event.InvoicePaidEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.Message;

@Service
@RequiredArgsConstructor
@Slf4j
public class InvoiceProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void sendInvoicePaidEvent(InvoicePaidEvent event) {
        log.info("Kafka'ya ödeme eventi gönderiliyor: {}", event);

        Message<InvoicePaidEvent> message = MessageBuilder
                .withPayload(event)
                .setHeader(KafkaHeaders.TOPIC, "invoice-paid-topic")
                .build();

        kafkaTemplate.send(message);
    }
}