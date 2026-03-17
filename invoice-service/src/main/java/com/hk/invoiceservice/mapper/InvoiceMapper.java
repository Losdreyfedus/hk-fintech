package com.hk.invoiceservice.mapper;

import com.hk.invoiceservice.dto.request.CreateInvoiceRequest;
import com.hk.invoiceservice.dto.response.InvoiceResponse;
import com.hk.invoiceservice.entity.Invoice;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface InvoiceMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "status", constant = "PENDING")
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    Invoice toEntity(CreateInvoiceRequest request);
    @Mapping(target = "createdDate", source = "createdAt")
    InvoiceResponse toResponse(Invoice invoice);
}