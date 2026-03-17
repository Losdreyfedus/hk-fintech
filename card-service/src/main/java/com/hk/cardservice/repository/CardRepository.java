package com.hk.cardservice.repository;

import com.hk.cardservice.entity.Card;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CardRepository extends JpaRepository<Card, Long> {
    List<Card> findAllByUserIdAndIsActiveTrue(Long userId);
    boolean existsByUserIdAndMaskedCardNumber(Long userId, String maskedCardNumber);
    Optional<Card> findByIdAndUserId(Long id, Long userId);
}