package com.hk-fintech.hk.walletservice.service;

import com.hk-fintech.hk.walletservice.client.PaymentClient;
import com.hk-fintech.hk.walletservice.dto.request.TopUpRequest;
import com.hk-fintech.hk.walletservice.entity.Wallet;
import com.hk-fintech.hk.walletservice.entity.WalletTransaction;
import com.hk-fintech.hk.walletservice.exception.InsufficientBalanceException;
import com.hk-fintech.hk.walletservice.exception.WalletNotFoundException;
import com.hk-fintech.hk.walletservice.repository.WalletRepository;
import com.hk-fintech.hk.walletservice.repository.WalletTransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("WalletService Unit Tests")
class WalletServiceTest {

    @Mock
    private WalletRepository walletRepository;

    @Mock
    private WalletTransactionRepository transactionRepository;

    @Mock
    private PaymentClient paymentClient;

    @InjectMocks
    private WalletService walletService;

    private static final Long USER_ID = 1L;
    private static final String WALLET_ID = "uuid-wallet-123";

    private Wallet testWallet;

    @BeforeEach
    void setUp() {
        testWallet = Wallet.builder()
                .id(WALLET_ID)
                .userId(USER_ID)
                .balance(new BigDecimal("500.00"))
                .currency("TRY")
                .build();
    }

    @Nested
    @DisplayName("Cüzdan Oluşturma (createWallet)")
    class CreateWalletTests {

        @Test
        @DisplayName("Kullanıcının cüzdanı yoksa → yeni cüzdan oluşturulmalı")
        void shouldCreateWallet_WhenUserDoesNotHaveOne() {
            // ARRANGE
            given(walletRepository.findByUserId(USER_ID)).willReturn(Optional.empty());
            given(walletRepository.save(any(Wallet.class))).willAnswer(inv -> inv.getArgument(0));

            // ACT
            walletService.createWallet(USER_ID);

            // ASSERT
            ArgumentCaptor<Wallet> captor = ArgumentCaptor.forClass(Wallet.class);
            verify(walletRepository).save(captor.capture());

            Wallet saved = captor.getValue();
            assertThat(saved.getUserId()).isEqualTo(USER_ID);
            assertThat(saved.getBalance()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(saved.getCurrency()).isEqualTo("TRY");
        }

        @Test
        @DisplayName("Kullanıcının zaten cüzdanı varsa → yeni cüzdan oluşturulMAMALI")
        void shouldNotCreateDuplicateWallet_WhenUserAlreadyHasOne() {
            // ARRANGE
            given(walletRepository.findByUserId(USER_ID)).willReturn(Optional.of(testWallet));

            // ACT
            walletService.createWallet(USER_ID);

            // ASSERT
            verify(walletRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("Cüzdan Sorgulama (getByUserId)")
    class GetByUserIdTests {

        @Test
        @DisplayName("Cüzdan bulunursa → Wallet objesi dönmeli")
        void shouldReturnWallet_WhenUserExists() {
            // ARRANGE
            given(walletRepository.findByUserId(USER_ID)).willReturn(Optional.of(testWallet));

            // ACT
            Wallet result = walletService.getByUserId(USER_ID);

            // ASSERT
            assertThat(result).isNotNull();
            assertThat(result.getUserId()).isEqualTo(USER_ID);
            assertThat(result.getBalance()).isEqualByComparingTo(new BigDecimal("500.00"));
        }

        @Test
        @DisplayName("Cüzdan bulunamazsa → WalletNotFoundException fırlatılmalı")
        void shouldThrowWalletNotFoundException_WhenUserDoesNotExist() {
            // ARRANGE
            given(walletRepository.findByUserId(999L)).willReturn(Optional.empty());

            // ACT & ASSERT
            assertThatThrownBy(() -> walletService.getByUserId(999L))
                    .isInstanceOf(WalletNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("Para Çekme (withdraw)")
    class WithdrawTests {

        @Test
        @DisplayName("Bakiye yeterliyse → bakiye düşmeli ve işlem geçmişi kaydedilmeli")
        void shouldDeductBalance_WhenSufficientFunds() {
            // ARRANGE
            BigDecimal withdrawAmount = new BigDecimal("200.00");
            given(walletRepository.findByUserId(USER_ID)).willReturn(Optional.of(testWallet));
            given(walletRepository.save(any(Wallet.class))).willAnswer(inv -> inv.getArgument(0));

            // ACT
            walletService.withdraw(USER_ID, withdrawAmount, "Fatura Ödemesi");

            // ASSERT
            assertThat(testWallet.getBalance()).isEqualByComparingTo(new BigDecimal("300.00"));
            verify(walletRepository).save(testWallet);
            verify(transactionRepository).save(any(WalletTransaction.class));
        }

        @Test
        @DisplayName("Bakiye yetersizse → InsufficientBalanceException fırlatılmalı")
        void shouldThrowInsufficientBalanceException_WhenInsufficientFunds() {
            // ARRANGE
            BigDecimal withdrawAmount = new BigDecimal("999.00");
            given(walletRepository.findByUserId(USER_ID)).willReturn(Optional.of(testWallet));

            // ACT & ASSERT
            assertThatThrownBy(() -> walletService.withdraw(USER_ID, withdrawAmount, "Büyük ödeme"))
                    .isInstanceOf(InsufficientBalanceException.class);

            verify(walletRepository, never()).save(any());
            verify(transactionRepository, never()).save(any());
        }

        @Test
        @DisplayName("Cüzdan bulunamazsa → WalletNotFoundException fırlatılmalı")
        void shouldThrowWalletNotFoundException_WhenWithdrawingFromNonExistentWallet() {
            // ARRANGE
            given(walletRepository.findByUserId(USER_ID)).willReturn(Optional.empty());

            // ACT & ASSERT
            assertThatThrownBy(() -> walletService.withdraw(USER_ID, BigDecimal.TEN, "Test"))
                    .isInstanceOf(WalletNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("Bakiye Yükleme (topUp)")
    class TopUpTests {

        @Test
        @DisplayName("Cüzdan varsa → Payment Service'e istek gitmeli")
        void shouldCallPaymentClient_WhenWalletExists() {
            // ARRANGE
            TopUpRequest request = new TopUpRequest(10L, new BigDecimal("100.00"));
            given(walletRepository.findByUserId(USER_ID)).willReturn(Optional.of(testWallet));

            // ACT
            walletService.topUp(request, USER_ID);

            // ASSERT
            verify(paymentClient).processTopUpPayment(any());
        }

        @Test
        @DisplayName("Cüzdan yoksa → WalletNotFoundException fırlatılmalı, Payment çağrılmamalı")
        void shouldThrowWalletNotFoundException_WhenTopUpWithoutWallet() {
            // ARRANGE
            TopUpRequest request = new TopUpRequest(10L, new BigDecimal("100.00"));
            given(walletRepository.findByUserId(USER_ID)).willReturn(Optional.empty());

            // ACT & ASSERT
            assertThatThrownBy(() -> walletService.topUp(request, USER_ID))
                    .isInstanceOf(WalletNotFoundException.class);

            verify(paymentClient, never()).processTopUpPayment(any());
        }
    }
}
