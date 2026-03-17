package com.hk.walletservice.exception;

import com.hk.common.exception.BusinessException;

public class WalletNotFoundException extends BusinessException {

    public WalletNotFoundException(Long userId) {
        super("Cüzdan bulunamadı! User ID: " + userId, 404);
    }
}
