<p align="center"><img src="docs/logo.svg" alt="Logo do Vigil: barras verticais com dois olhos vazados" width="120"></p>

# Vigil

**Vigil · monitor de status dos meus projetos.**

[![CI](https://github.com/Lakes777/vigil/actions/workflows/ci.yml/badge.svg)](https://github.com/Lakes777/vigil/actions/workflows/ci.yml)

API em Java com Spring Boot que verifica de tempos em tempos se os meus sites e APIs estão no ar,
guarda o histórico de cada verificação e calcula a disponibilidade de cada serviço. Quando algo
cai ou volta, avisa pelo Telegram (pelo [Sidekick](https://github.com/Lakes777/bot-utilidades)).

> Em construção. Fase atual: esqueleto do projeto (banco, migrações, CI).

## Tecnologias

- Java 21 e Spring Boot 4 (Web MVC, Data JPA, Validation, Actuator)
- PostgreSQL com migrações pelo Flyway
- Testes com JUnit 5 e Testcontainers (Postgres de verdade num contêiner)
- GitHub Actions

## Como rodar

Precisa do Java 21 e do Docker.

```bash
docker compose up -d     # sobe o Postgres na porta 5433
./mvnw spring-boot:run   # API em http://localhost:8080
```

Saúde da API: http://localhost:8080/actuator/health

Testes (o próprio Testcontainers sobe um Postgres temporário):

```bash
./mvnw verify
```

## Próximos passos

- [x] Esqueleto: Spring Boot, Postgres, Flyway, CI
- [ ] Cadastro de serviços (criar, listar, editar, remover) com validação e Swagger
- [ ] Verificação periódica com histórico (tempo de resposta, código HTTP)
- [ ] Disponibilidade em %, tempo médio e lista de quedas
- [ ] Rotas de administração protegidas (Spring Security)
- [ ] Alertas pelo Telegram quando um serviço cai ou volta
- [ ] Página pública de status e publicação com Docker
