package com.hk-fintech.hk.walletservice.service;

import com.hk-fintech.hk.walletservice.entity.Wallet;
import com.hk-fintech.hk.walletservice.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

import com.hk-fintech.hk.walletservice.repository.WalletTransactionRepository;
import com.hk-fintech.hk.walletservice.client.PaymentClient;
import com.hk-fintech.hk.walletservice.entity.TransactionType;
import com.hk-fintech.hk.walletservice.entity.WalletTransaction;
import com.hk-fintech.hk.walletservice.dto.request.TopUpRequest;
import com.hk-fintech.hk.walletservice.dto.request.CreatePaymentRequest;
import com.hk-fintech.hk.walletservice.exception.InsufficientBalanceException;
import com.hk-fintech.hk.walletservice.exception.WalletNotFoundException;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class WalletService {

    private final WalletRepository walletRepository;
    private final WalletTransactionRepository transactionRepository;
    private final PaymentClient paymentClient;

    public void createWallet(Long userId) {
        if (walletRepository.findByUserId(userId).isPresent()) {
            log.warn("Bu kullanıcının zaten cüzdanı var. İşlem atlanıyor. User ID: {}", userId);
            return;
        }
        Wallet wallet = Wallet.builder()
                .userId(userId)
                .balance(BigDecimal.ZERO)
                .currency("TRY")
                .build();

        walletRepository.save(wallet);
        log.info("✅ Cüzdan DB'ye kaydedildi. User ID: {}", userId);
    }

    public Wallet getByUserId(Long userId) {
        return walletRepository.findByUserId(userId)
                .orElseThrow(() -> new WalletNotFoundException(userId));
    }

    public void addTransactionHistory(String walletId, BigDecimal amount,
            TransactionType type, String referenceId, String description) {
        log.info("Cüzdan (ID: {}) için işlem geçmişi ekleniyor. Tutar: {}, Tür: {}", walletId, amount, type);
        WalletTransaction transaction = WalletTransaction
                .builder()
                .walletId(walletId)
                .amount(amount)
                .type(type)
                .referenceId(referenceId)
                .description(description)
                .build();

        transactionRepository.save(transaction);
        log.info("✅ İşlem geçmişi kaydedildi. ID: {}", transaction.getId());
    }

    public void topUp(TopUpRequest request, Long userId) {
        log.info("Cüzdana para yükleme isteği başlatıldı. User ID: {}, Tutar: {}", userId, request.amount());

        // 1. Önce kullanıcının cüzdanı var mı kontrol et (Yoksa hata fırlatır)
        getByUserId(userId);

        // 2. Payment Service'e istek at (Asenkron veya senkron kurguya göre)
        // Burada senkron olarak payment servise gidiyoruz.
        CreatePaymentRequest paymentRequest = CreatePaymentRequest
                .builder()
                .cardId(request.cardId())
                .amount(request.amount())
                // Top-Up işlemi olduğu için invoiceId vermiyoruz
                .build();

        paymentClient.processTopUpPayment(paymentRequest);
        log.info("Para yükleme için Payment Service tetiklendi. Bekleniyor...");
        // Bakiye artırımı Payment Service'den dönecek Kafka mesajı ile yapılacak
        // (TopUpCompletedEvent)
    }

    @Transactional
    public void withdraw(Long userId, BigDecimal amount, String description) {
        log.info("Cüzdandan para çekme işlemi başlatıldı. User ID: {}, Tutar: {}", userId, amount);

        Wallet wallet = getByUserId(userId);

        if (wallet.getBalance().compareTo(amount) < 0) {
            log.error("Yetersiz bakiye! Mevcut: {}, İstenen: {}", wallet.getBalance(), amount);
            throw new InsufficientBalanceException(wallet.getBalance(), amount);
        }

        wallet.setBalance(wallet.getBalance().subtract(amount));
        walletRepository.save(wallet);

        addTransactionHistory(
                wallet.getId(),
                amount,
                TransactionType.INVOICE_PAYMENT,
                null, // Reference ID can be null or filled if passed via Payment Service
                description);

        log.info("✅ Cüzdandan para çekildi. Yeni bakiye: {}", wallet.getBalance());
    }
}