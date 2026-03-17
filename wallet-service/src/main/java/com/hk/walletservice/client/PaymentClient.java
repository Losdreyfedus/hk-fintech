package com.hk.walletservice.client;

import com.hk.walletservice.dto.request.CreatePaymentRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "payment-service", url = "${payment.service.url:http://localhost:9093}")
public interface PaymentClient {

    @PostMapping("/api/v1/payments/top-up")
    void processTopUpPayment(@RequestBody CreatePaymentRequest request);
}
