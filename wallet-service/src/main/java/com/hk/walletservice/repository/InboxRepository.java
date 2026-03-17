package com.hk.walletservice.repository;

import com.hk.walletservice.entity.InboxMessage;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InboxRepository extends JpaRepository<InboxMessage, String> {
}