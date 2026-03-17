package com.hk.walletservice.entity; // Paket ismine dikkat

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal; // Para için her zaman BigDecimal kullanılır!

@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "wallets")
public class Wallet {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(name = "user_id", nullable = false, unique = true)
    private Long userId;
    @Column(nullable = false)
    private BigDecimal balance;

    @Column(name = "currency")
    private String currency;
}