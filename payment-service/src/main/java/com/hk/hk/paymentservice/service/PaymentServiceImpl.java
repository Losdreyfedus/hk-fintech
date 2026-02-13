package com.hk-fintech.hk.paymentservice.service;

import com.hk-fintech.hk.paymentservice.adapter.BankAdapter;
import com.hk-fintech.hk.paymentservice.client.CardServiceClient;
import com.hk-fintech.hk.paymentservice.dto.client.CardResponse;
import com.hk-fintech.hk.paymentservice.dto.request.CreatePaymentRequest;
import com.hk-fintech.hk.paymentservice.dto.response.PaymentResponse;
import com.hk-fintech.hk.paymentservice.entity.Payment;
import com.hk-fintech.hk.paymentservice.entity.PaymentStatus;
import com.hk-fintech.hk.paymentservice.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {

    private final PaymentRepository paymentRepository;
    private final CardServiceClient cardServiceClient;
    private final BankAdapter bankAdapter;

    @Override
    @Transactional
    public PaymentResponse processPayment(CreatePaymentRequest request, Long userId) {

        List<CardResponse> userCards = cardServiceClient.getAllCardsByUserId();


        CardResponse selectedCard = userCards.stream()
                .filter(card -> card.id().equals(request.cardId()))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Kart bulunamadı veya işlem yapmaya yetkiniz yok!"));


        boolean bankSuccess = bankAdapter.pay(selectedCard.cardToken(), request.amount());

        Payment payment = new Payment();
        payment.setUserId(userId);
        payment.setCardId(request.cardId());
        payment.setInvoiceId(request.invoiceId());
        payment.setAmount(request.amount());
        payment.setStatus(bankSuccess ? PaymentStatus.SUCCESS : PaymentStatus.FAILED);
        
        paymentRepository.save(payment);

        return new PaymentResponse(
                payment.getId(),
                payment.getInvoiceId(),
                payment.getAmount(),
                payment.getStatus(),
                payment.getCreatedDate()
        );
    }
}