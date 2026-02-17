package com.hk-fintech.hk.walletservice.service;

import com.hk-fintech.hk.walletservice.entity.Wallet;
import com.hk-fintech.hk.walletservice.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
@Slf4j
public class WalletService {

    private final WalletRepository walletRepository;


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
                .orElseThrow(() -> new RuntimeException("Cüzdan bulunamadı!"));
    }
}