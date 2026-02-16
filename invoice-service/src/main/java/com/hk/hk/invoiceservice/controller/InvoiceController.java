package com.hk-fintech.hk.invoiceservice.controller;

import com.hk-fintech.hk.invoiceservice.dto.request.CreateInvoiceRequest;
import com.hk-fintech.hk.invoiceservice.dto.response.InvoiceResponse;
import com.hk-fintech.hk.invoiceservice.service.InvoiceService;
import com.hk-fintech.hk.invoiceservice.service.InvoiceServiceImpl;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/invoices")
@RequiredArgsConstructor
public class InvoiceController {

    private final InvoiceServiceImpl invoiceService;


    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public InvoiceResponse createInvoice(@RequestBody @Valid CreateInvoiceRequest request, @AuthenticationPrincipal Long currentUserId) {
        if (!request.userId().equals(currentUserId)) {
            throw new RuntimeException("Hata: Başkası adına fatura oluşturamazsınız! (Token ID: " + currentUserId + ")");
        }
        return invoiceService.createInvoice(request);
    }

    @GetMapping
    @ResponseStatus(HttpStatus.OK)
    public List<InvoiceResponse> getMyInvoices(@AuthenticationPrincipal Long userId) {
        return invoiceService.getAllInvoices(userId);
    }
}