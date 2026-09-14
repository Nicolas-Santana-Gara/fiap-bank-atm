package com.fiap.bank.atm.application.service;

import com.fiap.bank.atm.application.dto.AccountInfoDTO;
import com.fiap.bank.atm.application.dto.TransactionDTO;
import com.fiap.bank.atm.domain.model.Account;
import com.fiap.bank.atm.domain.model.Money;
import com.fiap.bank.atm.domain.model.Transaction;
import com.fiap.bank.atm.domain.repository.AccountRepository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class AtmService {
    private final AccountRepository accountRepository;
    private UUID currentAccountId;

    public AtmService(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    public AccountInfoDTO authenticate(String accountNumber, String pin) {
        Account account = accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new com.fiap.bank.atm.application.exception.InvalidPinException(
                        "Conta não encontrada."));

        try {
            account.authenticate(pin);
        } catch (RuntimeException domainException) {
            accountRepository.salvar(account);
            throw translate(domainException);
        }

        accountRepository.salvar(account);
        currentAccountId = account.getId();
        return toDTO(account);
    }

    public void withdraw(BigDecimal amount) {
        Account account = currentAccount();
        try {
            account.withdraw(Money.of(amount));
        } catch (RuntimeException domainException) {
            throw translate(domainException);
        }
        accountRepository.salvar(account);
    }

    public void deposit(BigDecimal amount) {
        Account account = currentAccount();
        try {
            account.deposit(Money.of(amount));
        } catch (RuntimeException domainException) {
            throw translate(domainException);
        }
        accountRepository.salvar(account);
    }

    public void transfer(String targetAccountNumber, BigDecimal amount) {
        Account account = currentAccount();
        Account target = accountRepository.findByAccountNumber(targetAccountNumber)
                .orElseThrow(() -> new IllegalArgumentException("Conta de destino não encontrada."));

        try {
            account.transfer(target, Money.of(amount));
        } catch (RuntimeException domainException) {
            throw translate(domainException);
        }

        accountRepository.salvar(account);
        accountRepository.salvar(target);
    }

    public BigDecimal getBalance() {
        return currentAccount().getBalance().getAmount();
    }

    public BigDecimal getRemainingDailyLimit() {
        Account account = currentAccount();
        return account.getDailyWithdrawalLimit().minus(account.getTotalWithdrawnToday()).getAmount();
    }

    public List<TransactionDTO> getStatement() {
        return currentAccount().getTransactions().stream()
                .map(this::toDTO)
                .toList();
    }

    public void logout() {
        currentAccountId = null;
    }

    public Optional<AccountInfoDTO> getCurrentAccountInfo() {
        if (currentAccountId == null) {
            return Optional.empty();
        }
        return accountRepository.buscarPorId(currentAccountId).map(this::toDTO);
    }

    public boolean isAuthenticated() {
        return currentAccountId != null;
    }

    private Account currentAccount() {
        if (currentAccountId == null) {
            throw new IllegalStateException("Nenhum usuário está autenticado no momento.");
        }
        return accountRepository.buscarPorId(currentAccountId)
                .orElseThrow(() -> new IllegalStateException("Conta autenticada não foi encontrada."));
    }

    private AccountInfoDTO toDTO(Account account) {
        return new AccountInfoDTO(
                account.getId(),
                account.getAccountNumber(),
                account.getBalance().getAmount(),
                account.getDailyWithdrawalLimit().getAmount(),
                account.getTotalWithdrawnToday().getAmount(),
                account.isBlocked());
    }

    private TransactionDTO toDTO(Transaction transaction) {
        return new TransactionDTO(
                transaction.getId(),
                transaction.getType().getDescription(),
                transaction.getAmount().getAmount(),
                transaction.getDescription(),
                transaction.getTimestamp());
    }

    // converte as exceções do domain pras equivalentes daqui, já que a presentation não enxerga o domain
    private RuntimeException translate(RuntimeException domainException) {
        String message = domainException.getMessage();
        return switch (domainException) {
            case com.fiap.bank.atm.domain.exception.AccountBlockedException e ->
                new com.fiap.bank.atm.application.exception.AccountBlockedException(message);
            case com.fiap.bank.atm.domain.exception.InvalidPinException e ->
                new com.fiap.bank.atm.application.exception.InvalidPinException(message);
            case com.fiap.bank.atm.domain.exception.InsufficientFundsException e ->
                new com.fiap.bank.atm.application.exception.InsufficientFundsException(message);
            case com.fiap.bank.atm.domain.exception.DailyLimitExceededException e ->
                new com.fiap.bank.atm.application.exception.DailyLimitExceededException(message);
            default -> domainException;
        };
    }
}
