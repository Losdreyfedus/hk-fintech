package com.hk.walletservice.controller;

import com.hk.walletservice.dto.response.WalletResponse;
import com.hk.walletservice.entity.Wallet;
import com.hk.walletservice.service.WalletService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.hk.walletservice.dto.request.TopUpRequest;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

import java.math.BigDecimal;

@RestController
@RequestMapping("/api/v1/wallets")
@RequiredArgsConstructor
public class WalletController {

    private final WalletService walletService;

    @GetMapping("/user/{userId}")
    public ResponseEntity<WalletResponse> getWalletByUserId(@PathVariable Long userId) {
        Wallet wallet = walletService.getByUserId(userId);

        WalletResponse response = WalletResponse.builder()
                .id(wallet.getId())
                .userId(wallet.getUserId())
                .balance(wallet.getBalance())
                .currency(wallet.getCurrency())
                .build();

        return ResponseEntity.ok(response);
    }

    @PostMapping("/top-up")
    public ResponseEntity<Void> topUpWallet(
            @RequestBody @Valid TopUpRequest request,
            @AuthenticationPrincipal Long userId) {

        walletService.topUp(request, userId);
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/withdraw")
    public ResponseEntity<Void> withdrawFromWallet(
            @AuthenticationPrincipal Long userId,
            @RequestParam BigDecimal amount,
            @RequestParam String description) {

        walletService.withdraw(userId, amount, description);
        return ResponseEntity.ok().build();
    }
}