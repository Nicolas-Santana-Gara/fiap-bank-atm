@echo off
chcp 65001 > nul
echo ====================================================
echo        FIAP BANK - EMULADOR DE CAIXA ELETRÔNICO
echo ====================================================
echo.
echo Procurando o Maven do Apache NetBeans...

set MVN_PATH="C:\Program Files\Apache NetBeans\java\maven\bin\mvn.cmd"

if exist %MVN_PATH% (
    echo Maven encontrado! Compilando todos os modulos e iniciando a aplicacao...
    call %MVN_PATH% -q install -DskipTests
    call %MVN_PATH% -q -pl infrastructure exec:java
) else (
    echo.
    echo [AVISO] Maven do NetBeans não encontrado no caminho padrão.
    echo Tentando usar comando 'mvn' global...
    where mvn >nul 2>nul
    if %errorlevel% equ 0 (
        call mvn -q install -DskipTests
        call mvn -q -pl infrastructure exec:java
    ) else (
        echo [ERRO] Maven não encontrado. Por favor, abra este projeto
        echo no Apache NetBeans e execute-o diretamente pelo editor,
        echo ou instale o Maven e adicione-o ao seu PATH.
        pause
    )
)
