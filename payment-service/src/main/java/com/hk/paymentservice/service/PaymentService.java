package com.hk.paymentservice.service;

import com.hk.paymentservice.dto.request.CreatePaymentRequest;
import com.hk.paymentservice.dto.request.TopUpPaymentRequest;
import com.hk.paymentservice.dto.response.PaymentResponse;

public interface PaymentService {
    PaymentResponse processPayment(CreatePaymentRequest request, Long userId);
    PaymentResponse processTopUpPayment(TopUpPaymentRequest request, Long userId);
}