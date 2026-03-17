package com.hk.paymentservice.service;

import com.hk.paymentservice.adapter.BankAdapter;
import com.hk.paymentservice.client.CardServiceClient;
import com.hk.paymentservice.client.WalletServiceClient;
import com.hk.paymentservice.dto.client.CardResponse;
import com.hk.paymentservice.dto.request.CreatePaymentRequest;
import com.hk.paymentservice.dto.request.PaymentMethod;
import com.hk.paymentservice.dto.request.TopUpPaymentRequest;
import com.hk.paymentservice.dto.response.PaymentResponse;
import com.hk.paymentservice.entity.Payment;
import com.hk.paymentservice.entity.PaymentStatus;
import com.hk.paymentservice.exception.CardNotFoundException;
import com.hk.paymentservice.exception.PaymentFailedException;
import com.hk.paymentservice.kafka.producer.PaymentProducer;
import com.hk.paymentservice.repository.PaymentRepository;
import com.hk.common.events.kafka.PaymentCompletedEvent;
import com.hk.common.events.kafka.TopUpCompletedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentServiceImpl implements PaymentService {

    private final PaymentRepository paymentRepository;
    private final CardServiceClient cardServiceClient;
    private final BankAdapter bankAdapter;
    private final PaymentProducer paymentProducer;
    private final WalletServiceClient walletServiceClient;

    @Override
    @Transactional(noRollbackFor = PaymentFailedException.class)
    public PaymentResponse processPayment(CreatePaymentRequest request, Long userId) {

        if (PaymentMethod.WALLET.equals(request.paymentMethod())) {
            return processWalletPayment(request, userId);
        }

        return processCardPayment(request, userId);
    }

    @Override
    @Transactional(noRollbackFor = PaymentFailedException.class)
    public PaymentResponse processTopUpPayment(TopUpPaymentRequest request, Long userId) {

        CardResponse selectedCard = findUserCard(request.cardId());

        boolean bankSuccess = bankAdapter.pay(selectedCard.cardToken(), request.amount());

        Payment payment = buildPayment(userId, request.cardId(), null, request.amount(),
                bankSuccess ? PaymentStatus.SUCCESS : PaymentStatus.FAILED);
        paymentRepository.save(payment);

        if (!bankSuccess) {
            log.error("Top-Up bankadan reddedildi. User ID: {}, Tutar: {}", userId, request.amount());
            throw new PaymentFailedException("Banka işlemi reddetti.");
        }

        TopUpCompletedEvent event = new TopUpCompletedEvent(
                payment.getId(),
                payment.getUserId(),
                payment.getAmount(),
                payment.getStatus().name(),
                payment.getCreatedDate());
        paymentProducer.sendTopUpCompletedEvent(event);

        return toPaymentResponse(payment);
    }

    // ────────────────────────────────────────────────────────────────
    // Private helper methods
    // ────────────────────────────────────────────────────────────────

    private PaymentResponse processWalletPayment(CreatePaymentRequest request, Long userId) {
        log.info("Cüzdan ile ödeme başlatılıyor. UserID: {}, Tutar: {}", userId, request.amount());

        try {
            walletServiceClient.withdrawFromWallet(request.amount(),
                    "Fatura Ödemesi (Fatura ID: " + request.invoiceId() + ")");
        } catch (Exception e) {
            log.error("Cüzdan ile ödeme başarısız oldu! UserID: {}", userId, e);

            Payment failedPayment = buildPayment(userId, null, request.invoiceId(),
                    request.amount(), PaymentStatus.FAILED);
            paymentRepository.save(failedPayment);

            throw new PaymentFailedException("Cüzdan bakiyesi yetersiz veya cüzdan erişilemedi.");
        }

        Payment payment = buildPayment(userId, null, request.invoiceId(),
                request.amount(), PaymentStatus.SUCCESS);
        paymentRepository.save(payment);

        sendPaymentCompletedEvent(payment);
        return toPaymentResponse(payment);
    }

    private PaymentResponse processCardPayment(CreatePaymentRequest request, Long userId) {
        CardResponse selectedCard = findUserCard(request.cardId());

        boolean bankSuccess = bankAdapter.pay(selectedCard.cardToken(), request.amount());

        Payment payment = buildPayment(userId, request.cardId(), request.invoiceId(),
                request.amount(), bankSuccess ? PaymentStatus.SUCCESS : PaymentStatus.FAILED);
        paymentRepository.save(payment);

        if (!bankSuccess) {
            log.error("Banka ödemesi reddedildi. User ID: {}, Kart ID: {}", userId, request.cardId());
            throw new PaymentFailedException("Banka işlemi reddetti.");
        }

        sendPaymentCompletedEvent(payment);
        return toPaymentResponse(payment);
    }

    private CardResponse findUserCard(Long cardId) {
        List<CardResponse> userCards = cardServiceClient.getAllCardsByUserId();

        return userCards.stream()
                .filter(card -> card.id().equals(cardId))
                .findFirst()
                .orElseThrow(() -> new CardNotFoundException(cardId));
    }

    private Payment buildPayment(Long userId, Long cardId, Long invoiceId,
            java.math.BigDecimal amount, PaymentStatus status) {
        Payment payment = new Payment();
        payment.setUserId(userId);
        payment.setCardId(cardId);
        payment.setInvoiceId(invoiceId);
        payment.setAmount(amount);
        payment.setStatus(status);
        return payment;
    }

    private void sendPaymentCompletedEvent(Payment payment) {
        PaymentCompletedEvent event = new PaymentCompletedEvent(
                payment.getId(),
                payment.getInvoiceId(),
                payment.getUserId(),
                payment.getAmount(),
                payment.getStatus().name(),
                payment.getCreatedDate());
        paymentProducer.sendPaymentCompletedEvent(event);
    }

    private PaymentResponse toPaymentResponse(Payment payment) {
        return new PaymentResponse(
                payment.getId(),
                payment.getInvoiceId(),
                payment.getAmount(),
                payment.getStatus(),
                payment.getCreatedDate());
    }
}