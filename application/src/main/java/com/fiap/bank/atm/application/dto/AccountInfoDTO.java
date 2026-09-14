package com.fiap.bank.atm.application.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record AccountInfoDTO(
        UUID id,
        String accountNumber,
        BigDecimal balance,
        BigDecimal dailyWithdrawalLimit,
        BigDecimal totalWithdrawnToday,
        boolean blocked) {
}
