package com.hk-fintech.hk.walletservice.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "wallet_transactions")
public class WalletTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String walletId;

    @Column(nullable = false)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransactionType type;

    // Optional: İşlemin nereden tetiklendiğini takip etmek için (Örn: Invoice ID
    // veya Payment ID)
    private String referenceId;

    // Optional: İşlem açıklaması
    private String description;

    @CreationTimestamp
    private LocalDateTime createdDate;
}
