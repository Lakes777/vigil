<p align="center"><img src="docs/logo.svg" alt="Logo do Vigil: barras verticais com dois olhos vazados" width="120"></p>

# Vigil

**Vigil · monitor de status dos meus projetos.**

[![CI](https://github.com/Lakes777/vigil/actions/workflows/ci.yml/badge.svg)](https://github.com/Lakes777/vigil/actions/workflows/ci.yml)

API em Java com Spring Boot que verifica de tempos em tempos se os meus sites e APIs estão no ar,
guarda o histórico de cada verificação e calcula a disponibilidade de cada serviço. Quando algo
cai ou volta, avisa pelo Telegram (pelo [Sidekick](https://github.com/Lakes777/bot-utilidades)).

> Em construção. Pronto: cadastro de serviços. Próximo: as verificações periódicas.

## Tecnologias

- Java 21 e Spring Boot 4 (Web MVC, Data JPA, Validation, Actuator)
- Swagger / OpenAPI (springdoc)
- PostgreSQL com migrações pelo Flyway
- Testes com JUnit 5, Mockito e Testcontainers (Postgres de verdade num contêiner)
- GitHub Actions

## Como rodar

Precisa do Java 21 e do Docker.

```bash
docker compose up -d     # sobe o Postgres na porta 5433
./mvnw spring-boot:run   # API em http://localhost:8080
```

- Documentação interativa (Swagger): http://localhost:8080/docs
- Saúde da API: http://localhost:8080/actuator/health

## Rotas

| Método | Rota | O que faz |
|---|---|---|
| `GET` | `/api/servicos` | Lista os serviços em ordem alfabética |
| `GET` | `/api/servicos/{id}` | Busca um serviço |
| `POST` | `/api/servicos` | Cadastra (`nome`, `url`; opcionais `intervaloSegundos`, padrão 300, e `ativo`) |
| `PUT` | `/api/servicos/{id}` | Edita (`nome` e `url` obrigatórios; `intervaloSegundos` e `ativo` omitidos mantêm o valor atual) |
| `DELETE` | `/api/servicos/{id}` | Remove um serviço |

```bash
curl -X POST localhost:8080/api/servicos -H 'Content-Type: application/json' \
  -d '{"nome": "Coursebook", "url": "https://painel-estudos-cyan.vercel.app"}'
```

Erros seguem o formato padrão *problem detail* (RFC 9457), com a mensagem de cada campo inválido:

```json
{"status": 400, "title": "Dados inválidos", "detail": "Confira os campos indicados.",
 "campos": {"url": "use uma URL que comece com http:// ou https://"}}
```

O nome é único sem diferenciar maiúsculas ("Encore" e "encore" são o mesmo), garantido também
por um índice no banco.

## Organização

```
servico/   Servico (entidade) · ServicoRepository (banco) · ServicoService (regras)
           ServicoController (rotas HTTP) · ServicoEntrada/ServicoResposta (JSON)
erro/      TratadorDeErros (exceções -> respostas HTTP)
```

Testes (o próprio Testcontainers sobe um Postgres temporário):

```bash
./mvnw verify
```

## Próximos passos

- [x] Esqueleto: Spring Boot, Postgres, Flyway, CI
- [x] Cadastro de serviços (criar, listar, editar, remover) com validação e Swagger
- [ ] Verificação periódica com histórico (tempo de resposta, código HTTP)
- [ ] Disponibilidade em %, tempo médio e lista de quedas
- [ ] Rotas de administração protegidas (Spring Security)
- [ ] Alertas pelo Telegram quando um serviço cai ou volta
- [ ] Página pública de status e publicação com Docker
