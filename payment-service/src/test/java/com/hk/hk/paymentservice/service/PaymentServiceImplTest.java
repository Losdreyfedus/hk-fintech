package com.hk-fintech.hk.paymentservice.service;

import com.hk-fintech.hk.paymentservice.adapter.BankAdapter;
import com.hk-fintech.hk.paymentservice.client.CardServiceClient;
import com.hk-fintech.hk.paymentservice.client.WalletServiceClient;
import com.hk-fintech.hk.paymentservice.dto.client.CardResponse;
import com.hk-fintech.hk.paymentservice.dto.request.CreatePaymentRequest;
import com.hk-fintech.hk.paymentservice.dto.request.PaymentMethod;
import com.hk-fintech.hk.paymentservice.dto.request.TopUpPaymentRequest;
import com.hk-fintech.hk.paymentservice.dto.response.PaymentResponse;
import com.hk-fintech.hk.paymentservice.entity.Payment;
import com.hk-fintech.hk.paymentservice.entity.PaymentStatus;
import com.hk-fintech.hk.paymentservice.exception.CardNotFoundException;
import com.hk-fintech.hk.paymentservice.exception.PaymentFailedException;
import com.hk-fintech.hk.paymentservice.kafka.producer.PaymentProducer;
import com.hk-fintech.hk.paymentservice.repository.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("PaymentServiceImpl Unit Tests")
class PaymentServiceImplTest {

        @Mock
        private PaymentRepository paymentRepository;

        @Mock
        private CardServiceClient cardServiceClient;

        @Mock
        private BankAdapter bankAdapter;

        @Mock
        private PaymentProducer paymentProducer;

        @Mock
        private WalletServiceClient walletServiceClient;

        @InjectMocks
        private PaymentServiceImpl paymentService;

        private static final Long USER_ID = 1L;
        private static final Long CARD_ID = 10L;
        private static final Long INVOICE_ID = 100L;
        private static final BigDecimal AMOUNT = new BigDecimal("150.00");
        private static final String CARD_TOKEN = "tok_test_abc123";

        private CardResponse testCard;

        @BeforeEach
        void setUp() {
                testCard = new CardResponse(CARD_ID, "Enes K.", "5555-xxxx-xxxx-4444", CARD_TOKEN);
        }

        @Nested
        @DisplayName("Kart ile Ödeme (Card Payment)")
        class CardPaymentTests {

                @Test
                @DisplayName("Banka onaylarsa → ödeme SUCCESS olmalı ve Kafka event gönderilmeli")
                void shouldReturnSuccess_WhenBankApprovesCardPayment() {
                        // ARRANGE
                        CreatePaymentRequest request = new CreatePaymentRequest(
                                        INVOICE_ID, CARD_ID, AMOUNT, PaymentMethod.CARD);

                        given(cardServiceClient.getAllCardsByUserId()).willReturn(List.of(testCard));
                        given(bankAdapter.pay(CARD_TOKEN, AMOUNT)).willReturn(true);
                        given(paymentRepository.save(any(Payment.class)))
                                        .willAnswer(invocation -> {
                                                Payment p = invocation.getArgument(0);
                                                p.setId(1L);
                                                return p;
                                        });

                        // ACT
                        PaymentResponse response = paymentService.processPayment(request, USER_ID);

                        // ASSERT
                        assertThat(response).isNotNull();
                        assertThat(response.status()).isEqualTo(PaymentStatus.SUCCESS);
                        assertThat(response.amount()).isEqualByComparingTo(AMOUNT);

                        verify(paymentProducer, times(1)).sendPaymentCompletedEvent(any());
                        verify(paymentRepository, times(1)).save(any(Payment.class));
                }

                @Test
                @DisplayName("Banka reddederse → PaymentFailedException fırlatılmalı")
                void shouldThrowPaymentFailedException_WhenBankRejectsCardPayment() {
                        // ARRANGE
                        CreatePaymentRequest request = new CreatePaymentRequest(
                                        INVOICE_ID, CARD_ID, AMOUNT, PaymentMethod.CARD);

                        given(cardServiceClient.getAllCardsByUserId()).willReturn(List.of(testCard));
                        given(bankAdapter.pay(CARD_TOKEN, AMOUNT)).willReturn(false);
                        given(paymentRepository.save(any(Payment.class)))
                                        .willAnswer(invocation -> invocation.getArgument(0));

                        // ACT & ASSERT
                        assertThatThrownBy(() -> paymentService.processPayment(request, USER_ID))
                                        .isInstanceOf(PaymentFailedException.class);

                        // Audit trail
                        ArgumentCaptor<Payment> paymentCaptor = ArgumentCaptor.forClass(Payment.class);
                        verify(paymentRepository).save(paymentCaptor.capture());
                        assertThat(paymentCaptor.getValue().getStatus()).isEqualTo(PaymentStatus.FAILED);

                        verify(paymentProducer, never()).sendPaymentCompletedEvent(any());
                }

                @Test
                @DisplayName("Kullanıcının kartı bulunamazsa → CardNotFoundException fırlatılmalı")
                void shouldThrowCardNotFoundException_WhenCardIdDoesNotBelongToUser() {
                        // ARRANGE
                        CreatePaymentRequest request = new CreatePaymentRequest(
                                        INVOICE_ID, 999L, AMOUNT, PaymentMethod.CARD); // Olmayan kart ID

                        given(cardServiceClient.getAllCardsByUserId()).willReturn(List.of(testCard));

                        // ACT & ASSERT
                        assertThatThrownBy(() -> paymentService.processPayment(request, USER_ID))
                                        .isInstanceOf(CardNotFoundException.class);

                        verify(bankAdapter, never()).pay(anyString(), any());
                        verify(paymentRepository, never()).save(any());
                }
        }

        @Nested
        @DisplayName("Cüzdan ile Ödeme (Wallet Payment)")
        class WalletPaymentTests {

                @Test
                @DisplayName("Cüzdan bakiyesi yeterliyse → ödeme SUCCESS olmalı ve Kafka event gönderilmeli")
                void shouldReturnSuccess_WhenWalletHasSufficientBalance() {
                        CreatePaymentRequest request = new CreatePaymentRequest(
                                        INVOICE_ID, CARD_ID, AMOUNT, PaymentMethod.WALLET);

                        doNothing().when(walletServiceClient).withdrawFromWallet(any(), anyString());
                        given(paymentRepository.save(any(Payment.class)))
                                        .willAnswer(invocation -> {
                                                Payment p = invocation.getArgument(0);
                                                p.setId(2L);
                                                return p;
                                        });

                        PaymentResponse response = paymentService.processPayment(request, USER_ID);

                        assertThat(response).isNotNull();
                        assertThat(response.status()).isEqualTo(PaymentStatus.SUCCESS);

                        verify(paymentProducer, times(1)).sendPaymentCompletedEvent(any());
                        verify(bankAdapter, never()).pay(anyString(), any());
                }

                @Test
                @DisplayName("Cüzdan bakiyesi yetersizse → PaymentFailedException fırlatılmalı")
                void shouldThrowPaymentFailedException_WhenWalletBalanceIsInsufficient() {
                        // ARRANGE
                        CreatePaymentRequest request = new CreatePaymentRequest(
                                        INVOICE_ID, CARD_ID, AMOUNT, PaymentMethod.WALLET);

                        doThrow(new RuntimeException("Yetersiz bakiye"))
                                        .when(walletServiceClient).withdrawFromWallet(any(), anyString());
                        given(paymentRepository.save(any(Payment.class)))
                                        .willAnswer(invocation -> invocation.getArgument(0));

                        // ACT & ASSERT
                        assertThatThrownBy(() -> paymentService.processPayment(request, USER_ID))
                                        .isInstanceOf(PaymentFailedException.class);

                        ArgumentCaptor<Payment> paymentCaptor = ArgumentCaptor.forClass(Payment.class);
                        verify(paymentRepository).save(paymentCaptor.capture());
                        assertThat(paymentCaptor.getValue().getStatus()).isEqualTo(PaymentStatus.FAILED);

                        verify(paymentProducer, never()).sendPaymentCompletedEvent(any());
                }
        }

        @Nested
        @DisplayName("Bakiye Yükleme (Top-Up Payment)")
        class TopUpPaymentTests {

                @Test
                @DisplayName("Banka onaylarsa → top-up SUCCESS olmalı ve Kafka event gönderilmeli")
                void shouldReturnSuccess_WhenBankApprovesTopUp() {
                        // ARRANGE
                        TopUpPaymentRequest request = new TopUpPaymentRequest(CARD_ID, AMOUNT);

                        given(cardServiceClient.getAllCardsByUserId()).willReturn(List.of(testCard));
                        given(bankAdapter.pay(CARD_TOKEN, AMOUNT)).willReturn(true);
                        given(paymentRepository.save(any(Payment.class)))
                                        .willAnswer(invocation -> {
                                                Payment p = invocation.getArgument(0);
                                                p.setId(3L);
                                                return p;
                                        });

                        // ACT
                        PaymentResponse response = paymentService.processTopUpPayment(request, USER_ID);

                        // ASSERT
                        assertThat(response).isNotNull();
                        assertThat(response.status()).isEqualTo(PaymentStatus.SUCCESS);
                        assertThat(response.invoiceId()).isNull(); // Top-up'ta fatura ID olmaz

                        verify(paymentProducer, times(1)).sendTopUpCompletedEvent(any());
                }

                @Test
                @DisplayName("Banka reddederse → PaymentFailedException fırlatılmalı")
                void shouldThrowPaymentFailedException_WhenBankRejectsTopUp() {
                        // ARRANGE
                        TopUpPaymentRequest request = new TopUpPaymentRequest(CARD_ID, AMOUNT);

                        given(cardServiceClient.getAllCardsByUserId()).willReturn(List.of(testCard));
                        given(bankAdapter.pay(CARD_TOKEN, AMOUNT)).willReturn(false);
                        given(paymentRepository.save(any(Payment.class)))
                                        .willAnswer(invocation -> invocation.getArgument(0));

                        // ACT & ASSERT
                        assertThatThrownBy(() -> paymentService.processTopUpPayment(request, USER_ID))
                                        .isInstanceOf(PaymentFailedException.class);

                        ArgumentCaptor<Payment> paymentCaptor = ArgumentCaptor.forClass(Payment.class);
                        verify(paymentRepository).save(paymentCaptor.capture());
                        assertThat(paymentCaptor.getValue().getStatus()).isEqualTo(PaymentStatus.FAILED);
                        verify(paymentProducer, never()).sendTopUpCompletedEvent(any());
                }

                @Test
                @DisplayName("Olmayan bir kart ID ile top-up → CardNotFoundException fırlatılmalı")
                void shouldThrowCardNotFoundException_WhenTopUpCardDoesNotExist() {
                        // ARRANGE
                        TopUpPaymentRequest request = new TopUpPaymentRequest(999L, AMOUNT);

                        given(cardServiceClient.getAllCardsByUserId()).willReturn(List.of(testCard));

                        // ACT & ASSERT
                        assertThatThrownBy(() -> paymentService.processTopUpPayment(request, USER_ID))
                                        .isInstanceOf(CardNotFoundException.class);

                        verify(bankAdapter, never()).pay(anyString(), any());
                        verify(paymentRepository, never()).save(any());
                }
        }

        @Nested
        @DisplayName("Cross-Cutting Concerns")
        class BehavioralTests {

                @Test
                @DisplayName("Ödeme metodu WALLET ise → CardServiceClient hiç çağrılmamalı")
                void shouldNotCallCardService_WhenPaymentMethodIsWallet() {
                        // ARRANGE
                        CreatePaymentRequest request = new CreatePaymentRequest(
                                        INVOICE_ID, CARD_ID, AMOUNT, PaymentMethod.WALLET);

                        doNothing().when(walletServiceClient).withdrawFromWallet(any(), anyString());
                        given(paymentRepository.save(any(Payment.class)))
                                        .willAnswer(invocation -> {
                                                Payment p = invocation.getArgument(0);
                                                p.setId(4L);
                                                return p;
                                        });

                        // ACT
                        paymentService.processPayment(request, USER_ID);

                        // ASSERT
                        verify(cardServiceClient, never()).getAllCardsByUserId();
                        verify(bankAdapter, never()).pay(anyString(), any());
                }

                @Test
                @DisplayName("Başarılı kart ödemesinde Payment entity'si doğru alanlarla oluşturulmalı")
                void shouldBuildPaymentEntityCorrectly_WhenCardPaymentIsSuccessful() {
                        // ARRANGE
                        CreatePaymentRequest request = new CreatePaymentRequest(
                                        INVOICE_ID, CARD_ID, AMOUNT, PaymentMethod.CARD);

                        given(cardServiceClient.getAllCardsByUserId()).willReturn(List.of(testCard));
                        given(bankAdapter.pay(CARD_TOKEN, AMOUNT)).willReturn(true);
                        given(paymentRepository.save(any(Payment.class)))
                                        .willAnswer(invocation -> invocation.getArgument(0));

                        // ACT
                        paymentService.processPayment(request, USER_ID);

                        // ASSERT
                        ArgumentCaptor<Payment> captor = ArgumentCaptor.forClass(Payment.class);
                        verify(paymentRepository).save(captor.capture());

                        Payment savedPayment = captor.getValue();
                        assertThat(savedPayment.getUserId()).isEqualTo(USER_ID);
                        assertThat(savedPayment.getCardId()).isEqualTo(CARD_ID);
                        assertThat(savedPayment.getInvoiceId()).isEqualTo(INVOICE_ID);
                        assertThat(savedPayment.getAmount()).isEqualByComparingTo(AMOUNT);
                        assertThat(savedPayment.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
                }
        }
}
