package com.hk.walletservice.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record TopUpRequest(
        @NotNull(message = "Kart ID boş olamaz") Long cardId,

        @NotNull(message = "Tutar boş olamaz") @Positive(message = "Yükleme tutarı 0'dan büyük olmalıdır") BigDecimal amount) {
}
