package com.hk-fintech.hk.invoiceservice.client;

import com.hk-fintech.hk.invoiceservice.dto.request.PaymentRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "payment-service", url = "http://localhost:9093")
public interface PaymentClient {
    @PostMapping("/api/v1/payments")
    void processPayment(@RequestBody PaymentRequest request);
}