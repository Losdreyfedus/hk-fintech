package com.hk-fintech.hk.invoiceservice.client;

import com.hk-fintech.hk.invoiceservice.dto.request.PaymentRequest;
import com.hk-fintech.hk.invoiceservice.dto.response.PaymentResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "payment-service", url = "${payment.service.url:http://localhost:9093}")
public interface PaymentClient {
    @PostMapping("/api/v1/payments")
    PaymentResponse processPayment(@RequestBody PaymentRequest request);
}
