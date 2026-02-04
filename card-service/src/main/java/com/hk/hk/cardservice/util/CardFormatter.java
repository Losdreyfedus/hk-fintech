package com.hk-fintech.hk.cardservice.util;

import lombok.experimental.UtilityClass;

@UtilityClass
public class CardFormatter {
    public String maskCardNumber(String pan) {
        if (pan == null || pan.length() < 10) return pan;
        return pan.substring(0, 6) + "******" + pan.substring(pan.length() - 4);
    }
}