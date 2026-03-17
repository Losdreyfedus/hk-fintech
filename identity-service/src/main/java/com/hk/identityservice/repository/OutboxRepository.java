package com.hk.identityservice.repository;

import com.hk.identityservice.entity.OutboxMessage;
import com.hk.identityservice.entity.OutboxStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface OutboxRepository extends JpaRepository<OutboxMessage, Long> {
    List<OutboxMessage> findByStatusOrderByCreatedAtAsc(OutboxStatus status);
}