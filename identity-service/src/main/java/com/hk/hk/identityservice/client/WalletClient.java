package com.hk-fintech.hk.identityservice.client;

import com.hk-fintech.hk.identityservice.dto.request.CreateWalletRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "wallet-service", url = "http://localhost:9091/api/v1/wallets")
public interface WalletClient {

    @PostMapping
    ResponseEntity<Void> createWallet(@RequestBody CreateWalletRequest request);
}