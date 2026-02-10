package com.hk-fintech.hk.cardservice.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import org.hibernate.validator.constraints.LuhnCheck;

public record CreateCardRequest(
        @NotBlank(message = "Kart sahibi adı boş olamaz")
        String cardHolder,

        @NotBlank(message = "Kart numarası boş olamaz")
        @Pattern(regexp = "^[0-9]{16}$", message = "Kart numarası 16 haneli olmalıdır")
        @LuhnCheck(message = "Geçersiz kredi kartı numarası")
        String cardNumber,

        @NotBlank(message = "Ay bilgisi boş olamaz")
        @Pattern(regexp = "^(0[1-9]|1[0-2])$", message = "Ay 01-12 arasında olmalıdır")
        String expireMonth,

        @NotBlank(message = "Yıl bilgisi boş olamaz")
        @Pattern(regexp = "^20[2-9][0-9]$", message = "Geçerli bir yıl giriniz")
        String expireYear,

        @NotBlank(message = "CVV boş olamaz")
        @Pattern(regexp = "^[0-9]{3}$", message = "CVV 3 haneli olmalıdır")
        String cvv,

        String cardAlias
) {}