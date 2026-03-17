package com.hk.invoiceservice.service;

import com.hk.invoiceservice.client.PaymentClient;
import com.hk.invoiceservice.dto.request.CreateInvoiceRequest;
import com.hk.invoiceservice.dto.request.PayInvoiceRequest;
import com.hk.invoiceservice.dto.request.PaymentRequest;
import com.hk.invoiceservice.dto.response.InvoiceResponse;
import com.hk.invoiceservice.dto.response.PaymentResponse;
import com.hk.invoiceservice.entity.Invoice;
import com.hk.invoiceservice.entity.InvoiceStatus;
import com.hk.invoiceservice.event.InvoicePaidEvent;
import com.hk.invoiceservice.exception.InvoiceAlreadyPaidException;
import com.hk.invoiceservice.exception.InvoiceNotFoundException;
import com.hk.invoiceservice.exception.PaymentNotSuccessfulException;
import com.hk.invoiceservice.exception.UnauthorizedInvoiceAccessException;
import com.hk.invoiceservice.kafka.InvoiceProducer;
import com.hk.invoiceservice.mapper.InvoiceMapper;
import com.hk.invoiceservice.repository.InvoiceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class InvoiceServiceImpl implements InvoiceService {

    private static final String PAYMENT_STATUS_SUCCESS = "SUCCESS";

    private final InvoiceRepository invoiceRepository;
    private final InvoiceMapper invoiceMapper;
    private final PaymentClient paymentClient;
    private final InvoiceProducer invoiceProducer;

    @Override
    @Transactional
    public InvoiceResponse createInvoice(CreateInvoiceRequest request) {
        Invoice invoice = invoiceMapper.toEntity(request);
        invoice.setStatus(InvoiceStatus.PENDING);

        Invoice savedInvoice = invoiceRepository.save(invoice);
        log.info("Fatura oluşturuldu. ID: {}, User ID: {}", savedInvoice.getId(), savedInvoice.getUserId());

        return invoiceMapper.toResponse(savedInvoice);
    }

    @Override
    public List<InvoiceResponse> getAllInvoices(Long userId) {
        return invoiceRepository.findAllByUserId(userId)
                .stream()
                .map(invoiceMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional
    public void payInvoice(Long invoiceId, PayInvoiceRequest request, Long currentUserId) {
        Invoice invoice = invoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new InvoiceNotFoundException(invoiceId));

        validateInvoiceOwnership(invoice, currentUserId);
        validateInvoiceNotAlreadyPaid(invoice);

        PaymentResponse paymentResponse = paymentClient.processPayment(new PaymentRequest(
                invoice.getId(),
                request.cardId(),
                invoice.getAmount(),
                request.paymentMethod()));

        if (!PAYMENT_STATUS_SUCCESS.equals(paymentResponse.status())) {
            log.error("Ödeme başarısız! Fatura ID: {}, Ödeme Status: {}", invoiceId, paymentResponse.status());
            throw new PaymentNotSuccessfulException(invoiceId);
        }

        invoice.setStatus(InvoiceStatus.PAID);
        invoiceRepository.save(invoice);
        log.info("Fatura ödendi. ID: {}, User ID: {}", invoiceId, currentUserId);

        invoiceProducer.sendInvoicePaidEvent(new InvoicePaidEvent(
                invoice.getId(),
                invoice.getUserId(),
                invoice.getAmount(),
                invoice.getInstitutionName(),
                LocalDateTime.now()));
    }

    private void validateInvoiceOwnership(Invoice invoice, Long currentUserId) {
        if (!invoice.getUserId().equals(currentUserId)) {
            log.warn("Yetkisiz fatura erişimi! Fatura ID: {}, Fatura Sahibi: {}, İsteyen: {}",
                    invoice.getId(), invoice.getUserId(), currentUserId);
            throw new UnauthorizedInvoiceAccessException();
        }
    }

    private void validateInvoiceNotAlreadyPaid(Invoice invoice) {
        if (InvoiceStatus.PAID.equals(invoice.getStatus())) {
            log.warn("Zaten ödenmiş fatura tekrar ödeme denemesi! Fatura ID: {}", invoice.getId());
            throw new InvoiceAlreadyPaidException(invoice.getId());
        }
    }
}
