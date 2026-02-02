package com.hk-fintech.hk.identityservice.kafka.consumer;

import com.hk-fintech.hk.common.events.kafka.WalletCreationFailedEvent;
import com.hk-fintech.hk.identityservice.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class IdentityConsumer {
        //soft delete (prod proje)
    private final UserRepository userRepository;

    @KafkaListener(topics = "wallet-failed-topic", groupId = "identity-group")
    public void handleWalletCreationFailedEvent(WalletCreationFailedEvent event) {
        log.warn("❌ Rollback Event alındı! Kullanıcı silinecek. ID: {}", event.getUserId());

        if (userRepository.existsById(event.getUserId())) {
            userRepository.deleteById(event.getUserId());
            log.info("🗑️ Kullanıcı başarıyla silindi (Rollback tamamlandı). ID: {}", event.getUserId());
        } else {
            log.warn("Silinmek istenen kullanıcı zaten bulunamadı. ID: {}", event.getUserId());
        }
    }
}