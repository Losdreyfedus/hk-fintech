package com.hk-fintech.hk.paymentservice.service;

import com.hk-fintech.hk.paymentservice.dto.request.CreatePaymentRequest;
import com.hk-fintech.hk.paymentservice.dto.response.PaymentResponse;

public interface PaymentService {
    PaymentResponse processPayment(CreatePaymentRequest request, Long userId);
    PaymentResponse processTopUpPayment(com.hk-fintech.hk.paymentservice.dto.request.TopUpPaymentRequest request, Long userId);
}