package com.hk.paymentservice.controller;

import com.hk.paymentservice.dto.request.CreatePaymentRequest;
import com.hk.paymentservice.dto.request.TopUpPaymentRequest;
import com.hk.paymentservice.dto.response.PaymentResponse;
import com.hk.paymentservice.service.PaymentService;
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

    @PostMapping("/top-up")
    @ResponseStatus(HttpStatus.CREATED)
    public PaymentResponse processTopUpPayment(
            @RequestBody TopUpPaymentRequest request,
            @AuthenticationPrincipal Long userId
    ) {
        return paymentService.processTopUpPayment(request, userId);
    }
}