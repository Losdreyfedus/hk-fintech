package com.hk-fintech.hk.walletservice.dto.response;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;

@Data
@Builder
public class WalletResponse {
    private String id;
    private Long userId;
    private BigDecimal balance;
    private String currency;
}