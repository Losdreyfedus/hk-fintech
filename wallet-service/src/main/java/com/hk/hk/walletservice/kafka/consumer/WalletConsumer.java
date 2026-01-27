package com.hk-fintech.hk.walletservice.kafka.consumer;

import com.fasterxml.jackson.core.JsonProcessingException; // Hata sınıfı
import com.fasterxml.jackson.databind.ObjectMapper;         // Çevirici
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
    private final ObjectMapper objectMapper; // <-- EKLENDİ (String -> Object dönüşümü için)

    @KafkaListener(topics = "user-created-topic", groupId = "wallet-group-v1")
    public void consumeUserCreatedEvent(String message) { // <-- DEĞİŞTİ: Artık String alıyoruz
        log.info("📩 Kafka'dan ham mesaj geldi: {}", message);

        try {
            // 1. Gelen String (JSON) verisini Java Nesnesine çeviriyoruz (Manual Deserialization)
            UserCreatedEvent event = objectMapper.readValue(message, UserCreatedEvent.class);

            log.info("✅ Mesaj Java nesnesine çevrildi. User ID: {}", event.getUserId());

            // 2. Servise işi devret
            walletService.createWallet(event.getUserId());

        } catch (JsonProcessingException e) {
            log.error("💥 JSON Çevirme Hatası! Gelen veri bozuk olabilir: {}", message, e);
            // Burada yapılacak bir şey yok, veri bozuksa rollback de yapamayız çünkü ID'yi bile okuyamadık.

        } catch (Exception e) {
            log.error("💥 Cüzdan oluşturma hatası!", e);

            // Eğer JSON'u çevirebildik ama cüzdanı oluşturamadıysak ID'yi bulup rollback deneyebiliriz.
            // Ama basitlik adına şimdilik log basıp geçiyoruz.
            // Gelişmiş versiyonda burada event nesnesini yukarıda tanımlayıp try-catch dışında kullanmak gerekir.
        }
    }
}