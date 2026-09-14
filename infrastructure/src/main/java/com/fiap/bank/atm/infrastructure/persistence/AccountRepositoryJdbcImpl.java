package com.fiap.bank.atm.infrastructure.persistence;

import com.fiap.bank.atm.domain.model.Account;
import com.fiap.bank.atm.domain.model.Money;
import com.fiap.bank.atm.domain.model.Transaction;
import com.fiap.bank.atm.domain.model.TransactionType;
import com.fiap.bank.atm.domain.repository.AccountRepository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class AccountRepositoryJdbcImpl implements AccountRepository {

    private static final String DEFAULT_AGENCY = "0001";

    @Override
    public Optional<Account> buscarPorId(UUID id) {
        String sql = "SELECT * FROM tb_account WHERE id = ?";
        try (Connection connection = SqliteConnectionFactory.getConnection();
                PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, id.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(mapAccount(rs, connection));
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Falha ao buscar conta por id.", e);
        }
    }

    @Override
    public Optional<Account> findByAccountNumber(String accountNumber) {
        String sql = "SELECT * FROM tb_account WHERE number = ?";
        try (Connection connection = SqliteConnectionFactory.getConnection();
                PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, accountNumber);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(mapAccount(rs, connection));
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Falha ao buscar conta pelo número.", e);
        }
    }

    @Override
    public List<Account> buscarTodos() {
        String sql = "SELECT * FROM tb_account";
        List<Account> accounts = new ArrayList<>();
        try (Connection connection = SqliteConnectionFactory.getConnection();
                PreparedStatement ps = connection.prepareStatement(sql);
                ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                accounts.add(mapAccount(rs, connection));
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Falha ao buscar todas as contas.", e);
        }
        return accounts;
    }

    @Override
    public void salvar(Account entidade) {
        String upsert = """
                INSERT INTO tb_account
                    (id, agency, number, pin, balance, daily_withdrawal_limit, total_withdrawn_today, failed_attempts, status)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(id) DO UPDATE SET
                    balance = excluded.balance,
                    daily_withdrawal_limit = excluded.daily_withdrawal_limit,
                    total_withdrawn_today = excluded.total_withdrawn_today,
                    failed_attempts = excluded.failed_attempts,
                    status = excluded.status
                """;

        try (Connection connection = SqliteConnectionFactory.getConnection()) {
            try (PreparedStatement ps = connection.prepareStatement(upsert)) {
                ps.setString(1, entidade.getId().toString());
                ps.setString(2, DEFAULT_AGENCY);
                ps.setString(3, entidade.getAccountNumber());
                ps.setString(4, entidade.getPin());
                ps.setBigDecimal(5, entidade.getBalance().getAmount());
                ps.setBigDecimal(6, entidade.getDailyWithdrawalLimit().getAmount());
                ps.setBigDecimal(7, entidade.getTotalWithdrawnToday().getAmount());
                ps.setInt(8, entidade.getFailedAttempts());
                ps.setString(9, entidade.isBlocked() ? "BLOCKED" : "ACTIVE");
                ps.executeUpdate();
            }
            insertNewTransactions(connection, entidade);
        } catch (SQLException e) {
            throw new IllegalStateException("Falha ao salvar conta.", e);
        }
    }

    @Override
    public void remover(UUID id) {
        try (Connection connection = SqliteConnectionFactory.getConnection()) {
            try (PreparedStatement ps = connection.prepareStatement("DELETE FROM tb_transaction WHERE account_id = ?")) {
                ps.setString(1, id.toString());
                ps.executeUpdate();
            }
            try (PreparedStatement ps = connection.prepareStatement("DELETE FROM tb_account WHERE id = ?")) {
                ps.setString(1, id.toString());
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Falha ao remover conta.", e);
        }
    }

    private Account mapAccount(ResultSet rs, Connection connection) throws SQLException {
        UUID id = UUID.fromString(rs.getString("id"));
        String accountNumber = rs.getString("number");
        String pin = rs.getString("pin");
        Money balance = Money.of(rs.getBigDecimal("balance"));
        Money dailyLimit = Money.of(rs.getBigDecimal("daily_withdrawal_limit"));
        Money totalWithdrawnToday = Money.of(rs.getBigDecimal("total_withdrawn_today"));
        boolean blocked = "BLOCKED".equals(rs.getString("status"));
        int failedAttempts = rs.getInt("failed_attempts");

        List<Transaction> transactions = findTransactionsByAccountId(connection, id);

        return Account.reconstruct(id, accountNumber, pin, balance, dailyLimit, totalWithdrawnToday, blocked,
                failedAttempts, transactions);
    }

    private List<Transaction> findTransactionsByAccountId(Connection connection, UUID accountId) throws SQLException {
        String sql = "SELECT * FROM tb_transaction WHERE account_id = ? ORDER BY created_at ASC";
        List<Transaction> transactions = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, accountId.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    UUID id = UUID.fromString(rs.getString("id"));
                    TransactionType type = TransactionType.valueOf(rs.getString("type"));
                    Money amount = Money.of(rs.getBigDecimal("amount"));
                    String description = rs.getString("description");
                    LocalDateTime timestamp = rs.getTimestamp("created_at").toLocalDateTime();
                    transactions.add(new Transaction(id, timestamp, type, amount, description));
                }
            }
        }
        return transactions;
    }

    // só insere o que ainda não tá salvo, pra não duplicar a cada save()
    private void insertNewTransactions(Connection connection, Account account) throws SQLException {
        int existingCount = countTransactions(connection, account.getId());
        List<Transaction> all = account.getTransactions();
        if (existingCount >= all.size()) {
            return;
        }

        String insert = """
                INSERT INTO tb_transaction (id, account_id, type, amount, description, created_at)
                VALUES (?, ?, ?, ?, ?, ?)
                """;
        try (PreparedStatement ps = connection.prepareStatement(insert)) {
            for (Transaction transaction : all.subList(existingCount, all.size())) {
                ps.setString(1, transaction.getId().toString());
                ps.setString(2, account.getId().toString());
                ps.setString(3, transaction.getType().name());
                ps.setBigDecimal(4, transaction.getAmount().getAmount());
                ps.setString(5, transaction.getDescription());
                ps.setTimestamp(6, Timestamp.valueOf(transaction.getTimestamp()));
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    private int countTransactions(Connection connection, UUID accountId) throws SQLException {
        String sql = "SELECT COUNT(*) AS total FROM tb_transaction WHERE account_id = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, accountId.toString());
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt("total");
            }
        }
    }
}
