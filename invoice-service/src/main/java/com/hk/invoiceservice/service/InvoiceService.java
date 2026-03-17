package com.hk.invoiceservice.service;

import com.hk.invoiceservice.dto.request.CreateInvoiceRequest;
import com.hk.invoiceservice.dto.request.PayInvoiceRequest;
import com.hk.invoiceservice.dto.response.InvoiceResponse;
import java.util.List;

public interface InvoiceService {
    InvoiceResponse createInvoice(CreateInvoiceRequest request);
    List<InvoiceResponse> getAllInvoices(Long userId);
    void payInvoice(Long invoiceId, PayInvoiceRequest request, Long currentUserId);
}