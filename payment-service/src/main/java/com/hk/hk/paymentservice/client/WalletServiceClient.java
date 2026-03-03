package com.hk-fintech.hk.paymentservice.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.math.BigDecimal;

@FeignClient(name = "wallet-service", url = "${wallet.service.url:http://localhost:9091}")
public interface WalletServiceClient {

    @PostMapping("/api/v1/wallets/withdraw")
    void withdrawFromWallet(@RequestParam BigDecimal amount, @RequestParam String description);
}
