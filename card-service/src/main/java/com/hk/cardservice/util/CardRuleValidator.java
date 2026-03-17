package com.hk.cardservice.util;

import lombok.experimental.UtilityClass;
import java.time.YearMonth;

@UtilityClass
public class CardRuleValidator {

    public void validateExpiry(String month, String year) {
        try {
            int expMonth = Integer.parseInt(month);
            int expYear = Integer.parseInt(year);

            YearMonth cardExpiry = YearMonth.of(expYear, expMonth);
            YearMonth now = YearMonth.now();

            if (cardExpiry.isBefore(now)) {
                throw new RuntimeException("Kartın kullanım süresi dolmuş!");
            }
        } catch (NumberFormatException e) {
            throw new RuntimeException("Tarih formatı hatalı!");
        }
    }
}