# FIAP Bank - Emulador de Caixa Eletrônico (ATM)

Checkpoint 4 - Engenharia de Software / Domain Driven Design - Java (FIAP, 2026)

Refactoring do emulador de ATM original (Java Swing) para uma arquitetura em camadas, com
persistência em SQLite via JDBC, DTOs (Records) na fronteira da aplicação, repositório genérico
com Optional e uso de Streams no lugar de laços imperativos.

## Autor

Trabalho individual.

Nicolas Santana Gará - RM561461

## Visão geral

O frontend em Java Swing não foi alterado (nem layout, nem fluxo de telas). O que mudou foi o
motor por trás dele: o projeto era um monólito com tudo no mesmo pacote e persistência em memória
(perdia tudo ao reiniciar). Agora o código está dividido fisicamente em 4 módulos Maven e os dados
ficam salvos de verdade em um banco SQLite.

## Estrutura dos módulos

```
fiap-bank-atm/            (pom agregador, packaging=pom)
  domain/                 regras de negócio (Account, Money, Transaction, exceções, repositórios)
  application/            AtmService + DTOs (AccountInfoDTO, TransactionDTO)
  infrastructure/         persistência JDBC/SQLite + classe main
  presentation/           Swing (AtmFrame, ScreenState) - só depende de application
```

Dependências entre módulos:

```
presentation -> application -> domain
infrastructure -> domain, application, presentation
```

O `pom.xml` de `presentation` não tem nenhuma dependência pra `domain` nem `infrastructure`, só
pra `application`. A classe com o `main()` ficou em `infrastructure` porque é o único módulo que
pode enxergar todo mundo ao mesmo tempo (precisa montar o repositório JDBC e abrir a tela Swing).

## Decisões de implementação

- `AtmService` não recebe nem devolve `Account`/`Transaction`/`Money`. Só trafega `AccountInfoDTO`,
  `TransactionDTO`, `UUID`, `BigDecimal` e tipos primitivos.
- As exceções de negócio (`AccountBlockedException`, `InvalidPinException`, etc.) existem tanto em
  `domain.exception` quanto em `application.exception`. O `AtmService` pega a do domain e relança a
  de application, porque a presentation não pode importar nada do domain.
- `ATMRepository<T extends BaseEntity>` é a interface genérica com `buscarPorId`, `salvar`,
  `remover` e `buscarTodos`, todos usando `Optional` onde faz sentido. `AccountRepository` estende
  essa interface e adiciona `findByAccountNumber`.
- `AccountRepositoryJdbcImpl` usa só `PreparedStatement`/`ResultSet`, sem concatenar SQL em lugar
  nenhum.
- Como o construtor de `Account` valida regras de negócio, criei um `Account.reconstruct(...)`
  separado só pra remontar a conta com os dados que já vêm do banco (sem passar pelas validações de
  novo).

## Contas de teste

Carregadas automaticamente no banco (`fiapbank.db`) na primeira execução:

| Conta | PIN | Saldo inicial | Limite diário |
|---|---|---|---|
| 12345 | 1234 | R$ 5.000,00 | R$ 1.500,00 |
| 67890 | 5678 | R$ 1.200,00 | R$ 1.000,00 |
| 99999 | 9999 | R$ 50,00 | R$ 500,00 |

Diferente da versão original, esses dados continuam lá mesmo depois de fechar e abrir o programa
de novo.

## Tecnologias

- Java 21
- Swing + FlatLaf (UI, sem alteração)
- JDBC + SQLite (org.xerial:sqlite-jdbc), sem ORM
- JUnit 5 (testes do módulo domain)
- Maven multi-módulo

## Como rodar

Pré-requisito: JDK 21+ e Maven 3.8+.

```bash
mvn clean install
mvn -pl infrastructure exec:java
```

No Windows também dá pra usar o `run.bat`.

Pela IDE: abrir a pasta raiz como projeto Maven multi-módulo e rodar a classe
`com.fiap.bank.atm.infrastructure.AtmApplication`.

## Testes

```bash
mvn test
```

Cobrem as regras do `Account`: autenticação, bloqueio após 3 tentativas erradas, saque, limite
diário, depósito, transferência e a reconstrução via `reconstruct`.

## Créditos

Trabalho acadêmico - Checkpoint 4, Domain Driven Design - Java, FIAP, 2026.
Prof. Eduardo Ramos.
