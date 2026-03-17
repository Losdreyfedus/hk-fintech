package com.hk.cardservice.mapper;

import com.hk.cardservice.dto.request.CreateCardRequest;
import com.hk.cardservice.dto.response.CardResponse;
import com.hk.cardservice.entity.Card;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface CardMapper {

    @Mapping(target = "maskedCardNumber", ignore = true)
    @Mapping(target = "cardToken", ignore = true)

    Card toEntity(CreateCardRequest request);

    CardResponse toResponse(Card card);
}