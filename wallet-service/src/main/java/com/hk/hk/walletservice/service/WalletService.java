package com.hk-fintech.hk.walletservice.service;

import com.hk-fintech.hk.walletservice.entity.Wallet;
import com.hk-fintech.hk.walletservice.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class WalletService {

    private final WalletRepository walletRepository;

    // 1. Cüzdan Oluştur (Yeni üye olunca çağrılacak)
    public Wallet createWallet(Integer userId) {
        // Aynı kullanıcının zaten cüzdanı var mı?
        if (walletRepository.findByUserId(userId).isPresent()) {
            throw new RuntimeException("Bu kullanıcının zaten bir cüzdanı var! (User ID: " + userId + ")");
        }

        Wallet wallet = Wallet.builder()
                .userId(userId)
                .balance(BigDecimal.ZERO) // Başlangıç bakiyesi 0
                .currency("TRY")
                .build();

        return walletRepository.save(wallet);
    }

    // 2. Bakiyeyi Getir
    public Wallet getByUserId(Integer userId) {
        return walletRepository.findByUserId(userId)
                .orElseThrow(() -> new RuntimeException("Cüzdan bulunamadı!"));
    }
}