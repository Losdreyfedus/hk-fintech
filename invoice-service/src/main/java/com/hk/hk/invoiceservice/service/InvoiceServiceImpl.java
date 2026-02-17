package com.hk-fintech.hk.invoiceservice.service;

import com.hk-fintech.hk.invoiceservice.client.PaymentClient;
import com.hk-fintech.hk.invoiceservice.dto.request.CreateInvoiceRequest;
import com.hk-fintech.hk.invoiceservice.dto.request.PayInvoiceRequest;
import com.hk-fintech.hk.invoiceservice.dto.request.PaymentRequest;
import com.hk-fintech.hk.invoiceservice.dto.response.InvoiceResponse;
import com.hk-fintech.hk.invoiceservice.entity.Invoice;
import com.hk-fintech.hk.invoiceservice.entity.InvoiceStatus;
import com.hk-fintech.hk.invoiceservice.event.InvoicePaidEvent;
import com.hk-fintech.hk.invoiceservice.kafka.InvoiceProducer;
import com.hk-fintech.hk.invoiceservice.mapper.InvoiceMapper;
import com.hk-fintech.hk.invoiceservice.repository.InvoiceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class InvoiceServiceImpl implements InvoiceService {

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
                .orElseThrow(() -> new RuntimeException("Fatura bulunamadı! ID: " + invoiceId));

        if (!invoice.getUserId().equals(currentUserId)) {
            throw new RuntimeException("Hata: Bu fatura size ait değil, ödeyemezsiniz!");
        }

        if (InvoiceStatus.PAID.equals(invoice.getStatus())) {
            throw new RuntimeException("Bu fatura zaten ödenmiş!");
        }

        paymentClient.processPayment(new PaymentRequest(
                invoice.getId(),
                request.cardId(),
                invoice.getAmount()
        ));

        invoice.setStatus(InvoiceStatus.PAID);
        invoiceRepository.save(invoice);

        invoiceProducer.sendInvoicePaidEvent(new InvoicePaidEvent(
                invoice.getId(),
                invoice.getUserId(),
                invoice.getAmount(),
                invoice.getInstitutionName(),
                LocalDateTime.now()
        ));
    }
}