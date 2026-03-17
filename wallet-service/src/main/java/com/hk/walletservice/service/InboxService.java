package com.hk.walletservice.service;

import com.hk.walletservice.entity.InboxMessage;
import com.hk.walletservice.repository.InboxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class InboxService {

    private final InboxRepository inboxRepository;

    @Transactional
    public void processIdempotent(String messageId, String reason, Runnable action) {

        if (inboxRepository.existsById(messageId)) {
            log.info("🚫 Mesaj zaten işlenmiş, atlanıyor. ID: {}", messageId);
            return; // Hiçbir şey yapma, işlem başarılı sayılır (Idempotency)
        }

        try {
            action.run();
        } catch (Exception e) {
            log.error("❌ İşlem sırasında hata oluştu, Rollback yapılıyor. ID: {}", messageId);
            throw e;
        }

        // 3. Mesajı İşlendi Olarak İşaretle
        InboxMessage inboxMessage = new InboxMessage();
        inboxMessage.setMessageId(messageId);
        inboxMessage.setReason(reason);

        inboxRepository.save(inboxMessage);

        log.info("✅ İşlem başarıyla tamamlandı ve Inbox'a kaydedildi. ID: {}", messageId);
    }
}