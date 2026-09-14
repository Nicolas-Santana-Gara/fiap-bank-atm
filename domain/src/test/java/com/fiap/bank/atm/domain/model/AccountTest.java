package com.fiap.bank.atm.domain.model;

import com.fiap.bank.atm.domain.exception.AccountBlockedException;
import com.fiap.bank.atm.domain.exception.DailyLimitExceededException;
import com.fiap.bank.atm.domain.exception.InsufficientFundsException;
import com.fiap.bank.atm.domain.exception.InvalidPinException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AccountTest {

    private Account account;

    @BeforeEach
    void setUp() {
        account = new Account(UUID.randomUUID(), "12345", "1234", Money.of(1000.00), Money.of(500.00));
    }

    @Test
    void authenticateWithCorrectPinSucceeds() {
        account.authenticate("1234");
        assertEquals(0, account.getFailedAttempts());
    }

    @Test
    void authenticateWithWrongPinThrowsAndIncrementsAttempts() {
        assertThrows(InvalidPinException.class, () -> account.authenticate("0000"));
        assertEquals(1, account.getFailedAttempts());
    }

    @Test
    void accountBlocksAfterThreeFailedAttempts() {
        assertThrows(InvalidPinException.class, () -> account.authenticate("0000"));
        assertThrows(InvalidPinException.class, () -> account.authenticate("0000"));
        assertThrows(AccountBlockedException.class, () -> account.authenticate("0000"));
        assertTrue(account.isBlocked());
    }

    @Test
    void withdrawReducesBalanceAndRecordsTransaction() {
        account.withdraw(Money.of(100.00));
        assertEquals(Money.of(900.00), account.getBalance());
        assertEquals(1, account.getTransactions().size());
    }

    @Test
    void withdrawAboveBalanceThrowsInsufficientFunds() {
        assertThrows(InsufficientFundsException.class, () -> account.withdraw(Money.of(5000.00)));
    }

    @Test
    void withdrawAboveDailyLimitThrows() {
        assertThrows(DailyLimitExceededException.class, () -> account.withdraw(Money.of(600.00)));
    }

    @Test
    void depositIncreasesBalance() {
        account.deposit(Money.of(250.00));
        assertEquals(Money.of(1250.00), account.getBalance());
    }

    @Test
    void transferMovesMoneyBetweenAccounts() {
        Account target = new Account(UUID.randomUUID(), "67890", "5678", Money.of(0.00), Money.of(500.00));

        account.transfer(target, Money.of(300.00));

        assertEquals(Money.of(700.00), account.getBalance());
        assertEquals(Money.of(300.00), target.getBalance());
    }

    @Test
    void reconstructRestoresFullAggregateState() {
        account.withdraw(Money.of(100.00));

        Account reconstructed = Account.reconstruct(
                account.getId(), account.getAccountNumber(), account.getPin(), account.getBalance(),
                account.getDailyWithdrawalLimit(), account.getTotalWithdrawnToday(), account.isBlocked(),
                account.getFailedAttempts(), account.getTransactions());

        assertEquals(account.getBalance(), reconstructed.getBalance());
        assertEquals(account.getTransactions().size(), reconstructed.getTransactions().size());
        assertFalse(reconstructed.isBlocked());
    }
}
