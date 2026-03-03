package com.hk-fintech.hk.paymentservice.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Kafka topic yapılandırması.
 * <p>
 * Uygulama başlatıldığında gerekli topic'ler otomatik oluşturulur.
 */
@Configuration
public class KafkaTopicConfig {

    @Bean
    public NewTopic paymentCompletedTopic() {
        return new NewTopic("payment-completed-topic", 1, (short) 1);
    }

    @Bean
    public NewTopic topUpCompletedTopic() {
        return new NewTopic("topup-completed-topic", 1, (short) 1);
    }
}
