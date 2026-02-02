package com.hk-fintech.hk.walletservice.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "inbox_messages")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class InboxMessage {

    @Id
    @Column(name = "message_id", unique = true, nullable = false)
    private String messageId; // Kafka'dan gelen Event ID (UUID)

    @Column(name = "reason")
    private String reason; // Hangi işlem için? (Örn: "CREATE_WALLET", "TOP_UP_100")

    @CreationTimestamp
    @Column(name = "processed_at")
    private LocalDateTime processedAt;
}