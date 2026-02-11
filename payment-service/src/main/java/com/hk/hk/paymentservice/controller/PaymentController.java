package com.hk-fintech.hk.paymentservice.controller;

import com.hk-fintech.hk.paymentservice.dto.request.CreatePaymentRequest;
import com.hk-fintech.hk.paymentservice.dto.response.PaymentResponse;
import com.hk-fintech.hk.paymentservice.service.PaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PaymentResponse createPayment(
            @RequestBody @Valid CreatePaymentRequest request,
            @AuthenticationPrincipal Long userId
    ) {
        return paymentService.processPayment(request, userId);
    }
}