package com.hk.walletservice.repository;

import com.hk.walletservice.entity.WalletTransaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WalletTransactionRepository extends JpaRepository<WalletTransaction, Long> {
    List<WalletTransaction> findAllByWalletIdOrderByCreatedDateDesc(String walletId);
}
