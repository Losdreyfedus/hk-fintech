package com.hk-fintech.hk.invoiceservice.service;

import com.hk-fintech.hk.invoiceservice.client.PaymentClient;
import com.hk-fintech.hk.invoiceservice.dto.request.CreateInvoiceRequest;
import com.hk-fintech.hk.invoiceservice.dto.request.PayInvoiceRequest;
import com.hk-fintech.hk.invoiceservice.dto.request.PaymentMethod;
import com.hk-fintech.hk.invoiceservice.dto.request.PaymentRequest;
import com.hk-fintech.hk.invoiceservice.dto.response.InvoiceResponse;
import com.hk-fintech.hk.invoiceservice.dto.response.PaymentResponse;
import com.hk-fintech.hk.invoiceservice.entity.BillType;
import com.hk-fintech.hk.invoiceservice.entity.Invoice;
import com.hk-fintech.hk.invoiceservice.entity.InvoiceStatus;
import com.hk-fintech.hk.invoiceservice.exception.InvoiceAlreadyPaidException;
import com.hk-fintech.hk.invoiceservice.exception.InvoiceNotFoundException;
import com.hk-fintech.hk.invoiceservice.exception.PaymentNotSuccessfulException;
import com.hk-fintech.hk.invoiceservice.exception.UnauthorizedInvoiceAccessException;
import com.hk-fintech.hk.invoiceservice.kafka.InvoiceProducer;
import com.hk-fintech.hk.invoiceservice.mapper.InvoiceMapper;
import com.hk-fintech.hk.invoiceservice.repository.InvoiceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("InvoiceServiceImpl Unit Tests")
class InvoiceServiceImplTest {

    @Mock
    private InvoiceRepository invoiceRepository;

    @Mock
    private InvoiceMapper invoiceMapper;

    @Mock
    private PaymentClient paymentClient;

    @Mock
    private InvoiceProducer invoiceProducer;

    @InjectMocks
    private InvoiceServiceImpl invoiceService;

    private static final Long USER_ID = 1L;
    private static final Long INVOICE_ID = 100L;
    private static final BigDecimal AMOUNT = new BigDecimal("450.50");

    private Invoice testInvoice;

    @BeforeEach
    void setUp() {
        testInvoice = new Invoice();
        testInvoice.setId(INVOICE_ID);
        testInvoice.setUserId(USER_ID);
        testInvoice.setAmount(AMOUNT);
        testInvoice.setStatus(InvoiceStatus.PENDING);
        testInvoice.setAccountNumber("5321234567");
        testInvoice.setDescription("Şubat Faturası");
        testInvoice.setBillType(BillType.valueOf("ELECTRICITY"));
        testInvoice.setInstitutionName("Hk-fintech");
        testInvoice.setDueDate(LocalDate.now().plusDays(30));
    }

    @Nested
    @DisplayName("Fatura Oluşturma (createInvoice)")
    class CreateInvoiceTests {

        @Test
        @DisplayName("Geçerli istek ile → fatura oluşturulmalı ve PENDING durumunda kaydedilmeli")
        void shouldCreateInvoice_WithPendingStatus() {
            // ARRANGE
            CreateInvoiceRequest request = mock(CreateInvoiceRequest.class);
            Invoice mappedInvoice = new Invoice();
            InvoiceResponse expectedResponse = new InvoiceResponse(
                    1L, USER_ID, BillType.ELECTRICITY, "Hk-fintech",
                    "5321234567", "Şubat", AMOUNT, InvoiceStatus.PENDING,
                    LocalDate.now(), LocalDateTime.now());

            given(invoiceMapper.toEntity(request)).willReturn(mappedInvoice);
            given(invoiceRepository.save(mappedInvoice)).willReturn(mappedInvoice);
            given(invoiceMapper.toResponse(mappedInvoice)).willReturn(expectedResponse);

            // ACT
            InvoiceResponse response = invoiceService.createInvoice(request);

            // ASSERT
            assertThat(response).isNotNull();
            assertThat(mappedInvoice.getStatus()).isEqualTo(InvoiceStatus.PENDING);
            verify(invoiceRepository).save(mappedInvoice);
        }
    }

    @Nested
    @DisplayName("Faturaları Listeleme (getAllInvoices)")
    class GetAllInvoicesTests {

        @Test
        @DisplayName("Kullanıcının faturaları varsa → liste dönmeli")
        void shouldReturnInvoiceList_WhenUserHasInvoices() {
            // ARRANGE
            InvoiceResponse resp = mock(InvoiceResponse.class);
            given(invoiceRepository.findAllByUserId(USER_ID)).willReturn(List.of(testInvoice));
            given(invoiceMapper.toResponse(testInvoice)).willReturn(resp);

            // ACT
            List<InvoiceResponse> result = invoiceService.getAllInvoices(USER_ID);

            // ASSERT
            assertThat(result).hasSize(1);
        }

        @Test
        @DisplayName("Kullanıcının faturası yoksa → boş liste dönmeli")
        void shouldReturnEmptyList_WhenUserHasNoInvoices() {
            // ARRANGE
            given(invoiceRepository.findAllByUserId(USER_ID)).willReturn(List.of());

            // ACT
            List<InvoiceResponse> result = invoiceService.getAllInvoices(USER_ID);

            // ASSERT
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("Fatura Ödeme (payInvoice)")
    class PayInvoiceTests {

        @Test
        @DisplayName("Her şey doğruysa → fatura PAID olmalı ve Kafka event gönderilmeli")
        void shouldMarkInvoiceAsPaid_WhenPaymentIsSuccessful() {
            // ARRANGE
            PayInvoiceRequest request = new PayInvoiceRequest(10L, PaymentMethod.CARD);
            PaymentResponse paymentResponse = new PaymentResponse(
                    1L, INVOICE_ID, AMOUNT, "SUCCESS", LocalDateTime.now());

            given(invoiceRepository.findById(INVOICE_ID)).willReturn(Optional.of(testInvoice));
            given(paymentClient.processPayment(any(PaymentRequest.class))).willReturn(paymentResponse);

            // ACT
            invoiceService.payInvoice(INVOICE_ID, request, USER_ID);

            // ASSERT
            assertThat(testInvoice.getStatus()).isEqualTo(InvoiceStatus.PAID);
            verify(invoiceRepository).save(testInvoice);
            verify(invoiceProducer).sendInvoicePaidEvent(any());
        }

        @Test
        @DisplayName("Fatura bulunamazsa → InvoiceNotFoundException fırlatılmalı")
        void shouldThrowInvoiceNotFoundException_WhenInvoiceDoesNotExist() {
            // ARRANGE
            PayInvoiceRequest request = new PayInvoiceRequest(10L, PaymentMethod.CARD);
            given(invoiceRepository.findById(999L)).willReturn(Optional.empty());

            // ACT & ASSERT
            assertThatThrownBy(() -> invoiceService.payInvoice(999L, request, USER_ID))
                    .isInstanceOf(InvoiceNotFoundException.class);

            verify(paymentClient, never()).processPayment(any());
        }

        @Test
        @DisplayName("Başka kullanıcının faturasına erişim → UnauthorizedInvoiceAccessException")
        void shouldThrowUnauthorizedAccess_WhenUserDoesNotOwnInvoice() {
            // ARRANGE
            Long differentUserId = 999L;
            PayInvoiceRequest request = new PayInvoiceRequest(10L, PaymentMethod.CARD);
            given(invoiceRepository.findById(INVOICE_ID)).willReturn(Optional.of(testInvoice));

            // ACT & ASSERT
            assertThatThrownBy(() -> invoiceService.payInvoice(INVOICE_ID, request, differentUserId))
                    .isInstanceOf(UnauthorizedInvoiceAccessException.class);

            verify(paymentClient, never()).processPayment(any());
        }

        @Test
        @DisplayName("Zaten ödenmiş fatura → InvoiceAlreadyPaidException fırlatılmalı")
        void shouldThrowAlreadyPaid_WhenInvoiceIsAlreadyPaid() {
            // ARRANGE
            testInvoice.setStatus(InvoiceStatus.PAID);
            PayInvoiceRequest request = new PayInvoiceRequest(10L, PaymentMethod.CARD);
            given(invoiceRepository.findById(INVOICE_ID)).willReturn(Optional.of(testInvoice));

            // ACT & ASSERT
            assertThatThrownBy(() -> invoiceService.payInvoice(INVOICE_ID, request, USER_ID))
                    .isInstanceOf(InvoiceAlreadyPaidException.class);

            verify(paymentClient, never()).processPayment(any());
        }

        @Test
        @DisplayName("Ödeme başarısız dönerse → PaymentNotSuccessfulException fırlatılmalı")
        void shouldThrowPaymentNotSuccessful_WhenPaymentFails() {
            // ARRANGE
            PayInvoiceRequest request = new PayInvoiceRequest(10L, PaymentMethod.CARD);
            PaymentResponse failedResponse = new PaymentResponse(
                    1L, INVOICE_ID, AMOUNT, "FAILED", LocalDateTime.now());

            given(invoiceRepository.findById(INVOICE_ID)).willReturn(Optional.of(testInvoice));
            given(paymentClient.processPayment(any(PaymentRequest.class))).willReturn(failedResponse);

            // ACT & ASSERT
            assertThatThrownBy(() -> invoiceService.payInvoice(INVOICE_ID, request, USER_ID))
                    .isInstanceOf(PaymentNotSuccessfulException.class);

            assertThat(testInvoice.getStatus()).isEqualTo(InvoiceStatus.PENDING);
            verify(invoiceRepository, never()).save(any());
            verify(invoiceProducer, never()).sendInvoicePaidEvent(any());
        }
    }
}
