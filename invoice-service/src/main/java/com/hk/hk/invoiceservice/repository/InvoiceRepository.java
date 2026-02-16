package com.hk-fintech.hk.invoiceservice.repository;

import com.hk-fintech.hk.invoiceservice.entity.Invoice;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface InvoiceRepository extends JpaRepository<Invoice, Long> {
    List<Invoice> findAllByUserId(Long userId);
}