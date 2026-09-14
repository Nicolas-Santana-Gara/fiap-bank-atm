package com.fiap.bank.atm.infrastructure.persistence;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

public final class SqliteConnectionFactory {
    private static final String DB_URL = "jdbc:sqlite:fiapbank.db";

    static {
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("Driver JDBC do SQLite não encontrado no classpath.", e);
        }
    }

    private SqliteConnectionFactory() {
    }

    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(DB_URL);
    }

    public static void close(Connection connection) {
        if (connection == null) {
            return;
        }
        try {
            connection.close();
        } catch (SQLException e) {
            System.err.println("Falha ao encerrar conexão com o banco de dados: " + e.getMessage());
        }
    }

    public static void initializeSchema() {
        String createAccountTable = """
                CREATE TABLE IF NOT EXISTS tb_account (
                    id VARCHAR(36) PRIMARY KEY,
                    agency VARCHAR(10) NOT NULL,
                    number VARCHAR(20) NOT NULL UNIQUE,
                    pin VARCHAR(4) NOT NULL,
                    balance DECIMAL(15, 2) NOT NULL,
                    daily_withdrawal_limit DECIMAL(15, 2) NOT NULL,
                    total_withdrawn_today DECIMAL(15, 2) NOT NULL,
                    failed_attempts INTEGER NOT NULL,
                    status VARCHAR(20) NOT NULL
                )
                """;

        String createTransactionTable = """
                CREATE TABLE IF NOT EXISTS tb_transaction (
                    id VARCHAR(36) PRIMARY KEY,
                    account_id VARCHAR(36) NOT NULL,
                    type VARCHAR(20) NOT NULL,
                    amount DECIMAL(15, 2) NOT NULL,
                    description VARCHAR(255) NOT NULL,
                    created_at TIMESTAMP NOT NULL,
                    FOREIGN KEY (account_id) REFERENCES tb_account(id)
                )
                """;

        try (Connection connection = getConnection(); Statement statement = connection.createStatement()) {
            statement.execute(createAccountTable);
            statement.execute(createTransactionTable);
            seedInitialData(connection);
        } catch (SQLException e) {
            throw new IllegalStateException("Falha ao inicializar o esquema do banco de dados.", e);
        }
    }

    private static void seedInitialData(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery("SELECT COUNT(*) AS total FROM tb_account")) {
            resultSet.next();
            if (resultSet.getInt("total") > 0) {
                return; // Banco já populado, evita duplicar a carga inicial a cada execução
            }
        }

        String insertAccount = """
                INSERT INTO tb_account
                    (id, agency, number, pin, balance, daily_withdrawal_limit, total_withdrawn_today, failed_attempts, status)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;

        try (PreparedStatement ps = connection.prepareStatement(insertAccount)) {
            seedAccount(ps, "12345", "1234", "5000.00", "1500.00");
            seedAccount(ps, "67890", "5678", "1200.00", "1000.00");
            seedAccount(ps, "99999", "9999", "50.00", "500.00");
        }
    }

    private static void seedAccount(PreparedStatement ps, String number, String pin,
            String balance, String dailyLimit) throws SQLException {
        ps.setString(1, UUID.randomUUID().toString());
        ps.setString(2, "0001");
        ps.setString(3, number);
        ps.setString(4, pin);
        ps.setBigDecimal(5, new BigDecimal(balance));
        ps.setBigDecimal(6, new BigDecimal(dailyLimit));
        ps.setBigDecimal(7, BigDecimal.ZERO.setScale(2));
        ps.setInt(8, 0);
        ps.setString(9, "ACTIVE");
        ps.executeUpdate();
    }
}
