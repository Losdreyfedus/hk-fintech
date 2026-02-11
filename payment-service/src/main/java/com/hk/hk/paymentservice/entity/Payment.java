package com.hk-fintech.hk.paymentservice.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "payments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "invoice_id", nullable = false)
    private Long invoiceId;

    @Column(name = "user_id", nullable = false)
    private Long userId;    // Kim ödüyor?

    @Column(name = "card_id", nullable = false)
    private Long cardId;    // Hangi kartla?

    @Column(nullable = false)
    private BigDecimal amount; // Kaç para?

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus status; // Durum ne?

    private LocalDateTime createdDate;

    // Otomatik tarih atama
    @PrePersist
    protected void onCreate() {
        createdDate = LocalDateTime.now();
    }
}