package com.hk-fintech.hk.walletservice.repository;

import com.hk-fintech.hk.walletservice.entity.OutboxMessage;
import com.hk-fintech.hk.walletservice.entity.OutboxStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface OutboxRepository extends JpaRepository<OutboxMessage, Long> {
    List<OutboxMessage> findByStatusOrderByCreatedAtAsc(OutboxStatus status);
}