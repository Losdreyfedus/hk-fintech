package com.hk-fintech.hk.invoiceservice.service;

import com.hk-fintech.hk.invoiceservice.dto.request.CreateInvoiceRequest;
import com.hk-fintech.hk.invoiceservice.dto.response.InvoiceResponse;
import com.hk-fintech.hk.invoiceservice.entity.Invoice;
import com.hk-fintech.hk.invoiceservice.entity.InvoiceStatus;
import com.hk-fintech.hk.invoiceservice.mapper.InvoiceMapper;
import com.hk-fintech.hk.invoiceservice.repository.InvoiceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class InvoiceServiceImpl implements InvoiceService {

    private final InvoiceRepository invoiceRepository;
    private final InvoiceMapper invoiceMapper;

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
}