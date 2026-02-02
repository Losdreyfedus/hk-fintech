package com.hk-fintech.hk.walletservice.scheduler;

import com.hk-fintech.hk.walletservice.entity.OutboxMessage;
import com.hk-fintech.hk.walletservice.entity.OutboxStatus;
import com.hk-fintech.hk.walletservice.repository.OutboxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxPublisher {

    private final OutboxRepository outboxRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Scheduled(fixedRate = 5000)
    public void publishPendingMessages() {
        List<OutboxMessage> pendingMessages = outboxRepository.findByStatusOrderByCreatedAtAsc(OutboxStatus.PENDING);

        if (!pendingMessages.isEmpty()) {
            log.info("🕒 Outbox Job Çalıştı: {} adet bekleyen mesaj bulundu.", pendingMessages.size());
        }

        for (OutboxMessage msg : pendingMessages) {
            try {
                kafkaTemplate.send(msg.getTopic(), msg.getPayload());

                msg.setStatus(OutboxStatus.SENT);
                outboxRepository.save(msg);

                //outboxRepository.delete(msg);

                log.info("✅ Mesaj Kafka'ya başarıyla iletildi. Outbox ID: {}", msg.getId());

            } catch (Exception e) {
                log.error("⚠️ Kafka'ya gönderim başarısız. Sonra tekrar denenecek. ID: {}", msg.getId());
            }
        }
    }
}