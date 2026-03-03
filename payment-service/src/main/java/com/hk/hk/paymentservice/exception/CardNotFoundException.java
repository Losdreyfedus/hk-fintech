package com.hk-fintech.hk.paymentservice.exception;

import com.hk-fintech.hk.common.exception.BusinessException;

public class CardNotFoundException extends BusinessException {

    public CardNotFoundException(Long cardId) {
        super("Kart bulunamadı veya işlem yapmaya yetkiniz yok! Kart ID: " + cardId, 404);
    }
}
