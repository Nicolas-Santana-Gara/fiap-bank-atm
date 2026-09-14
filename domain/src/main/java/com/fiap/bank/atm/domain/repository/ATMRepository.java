package com.fiap.bank.atm.domain.repository;

import com.fiap.bank.atm.domain.model.BaseEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ATMRepository<T extends BaseEntity> {
    Optional<T> buscarPorId(UUID id);

    void salvar(T entidade);

    void remover(UUID id);

    List<T> buscarTodos();
}
