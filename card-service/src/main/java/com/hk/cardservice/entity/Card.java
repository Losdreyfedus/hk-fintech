package com.hk.cardservice.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "cards")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Card {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "card_holder", nullable = false)
    private String cardHolder;

    @Column(name = "masked_card_number", nullable = false)
    private String maskedCardNumber;

    @Column(name = "expire_month", nullable = false, length = 2)
    private String expireMonth;

    @Column(name = "expire_year", nullable = false, length = 4)
    private String expireYear;

    @Column(name = "card_token", nullable = false)
    private String cardToken;

    @Column(name = "card_alias")
    private String cardAlias;

    @Builder.Default
    @Column(name = "is_active")
    private Boolean isActive = true;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}