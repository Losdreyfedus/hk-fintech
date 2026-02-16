package com.hk-fintech.hk.invoiceservice.service;

import com.hk-fintech.hk.invoiceservice.dto.request.CreateInvoiceRequest;
import com.hk-fintech.hk.invoiceservice.dto.response.InvoiceResponse;
import java.util.List;

public interface InvoiceService {
    InvoiceResponse createInvoice(CreateInvoiceRequest request);
    List<InvoiceResponse> getAllInvoices(Long userId);
}