<p align="center"><img src="docs/logo.svg" alt="Logo do Vigil: barras verticais com dois olhos vazados" width="120"></p>

# Vigil

**Vigil · monitor de status dos meus projetos.**

[![CI](https://github.com/Lakes777/vigil/actions/workflows/ci.yml/badge.svg)](https://github.com/Lakes777/vigil/actions/workflows/ci.yml)

API em Java com Spring Boot que verifica de tempos em tempos se os meus sites e APIs estão no ar,
guarda o histórico de cada verificação e calcula a disponibilidade de cada serviço. Quando algo
cai ou volta, avisa pelo Telegram (pelo [Sidekick](https://github.com/Lakes777/bot-utilidades)).

> Em construção. Pronto: cadastro, verificações periódicas e disponibilidade. Próximo: segurança.

## Tecnologias

- Java 21 e Spring Boot 4 (Web MVC, Data JPA, Validation, Actuator)
- Swagger / OpenAPI (springdoc)
- PostgreSQL com migrações pelo Flyway
- Testes com JUnit 5, Mockito, Testcontainers (Postgres de verdade num contêiner) e WireMock (sites
  falsos: lentos, fora do ar, com erro) (Postgres de verdade num contêiner)
- GitHub Actions

## Como rodar

Precisa do Java 21 e do Docker.

```bash
docker compose up -d     # sobe o Postgres na porta 5433
./mvnw spring-boot:run   # API em http://localhost:8080
```

- Documentação interativa (Swagger): http://localhost:8080/docs
- Saúde da API: http://localhost:8080/actuator/health

Testes (o próprio Testcontainers sobe um Postgres temporário):

```bash
./mvnw verify
```

## Rotas

| Método | Rota | O que faz |
|---|---|---|
| `GET` | `/api/servicos` | Lista os serviços em ordem alfabética |
| `GET` | `/api/servicos/{id}` | Busca um serviço |
| `POST` | `/api/servicos` | Cadastra (`nome`, `url`; opcionais `intervaloSegundos`, padrão 300, e `ativo`) |
| `PUT` | `/api/servicos/{id}` | Edita (`nome` e `url` obrigatórios; `intervaloSegundos` e `ativo` omitidos mantêm o valor atual) |
| `DELETE` | `/api/servicos/{id}` | Remove um serviço (e o histórico dele) |
| `GET` | `/api/servicos/{id}/verificacoes?limite=50` | Últimas verificações, da mais recente (limite de 1 a 500) |
| `POST` | `/api/servicos/{id}/verificar` | Verifica agora, sem esperar o intervalo |
| `GET` | `/api/status` | Todos os serviços: situação atual e disponibilidade em 24 h, 7 e 30 dias |
| `GET` | `/api/servicos/{id}/resumo` | O mesmo, de um serviço |
| `GET` | `/api/servicos/{id}/quedas?dias=30` | Quedas da mais recente para a mais antiga (1 a 90 dias) |

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

## Como as verificações funcionam

A cada 10 segundos (o "tique") o `Agendador` pede ao `Verificador` os serviços ativos cuja hora
de verificar chegou. Cada um é acessado pela `Sonda` numa **thread virtual** do Java 21, todos em
paralelo, então um site lento não atrasa os outros. O resultado vai para a tabela `verificacao`,
e a próxima verificação fica marcada para daqui a `intervaloSegundos`.

```json
{"feitaEm": "2026-10-06T18:07:41Z", "noAr": false, "codigoHttp": null, "tempoMs": 10004,
 "erro": "tempo esgotado (10 s)"}
```

- **No ar:** respondeu com código 2xx ou 3xx (redirecionamentos são seguidos).
- **Fora:** código 4xx/5xx, tempo esgotado (10 s), conexão recusada ou erro de rede.
- As chamadas HTTP ficam **fora de transação**: esperar um site lento não segura uma conexão do banco.
- Só os cabeçalhos da resposta são lidos: um site que mandasse o conteúdo devagar não prende a verificação.
- Editar um serviço o coloca para verificar na hora (a URL pode ter mudado), mesmo que uma verificação
  esteja em andamento: ela só agenda a próxima se ninguém editou o serviço no meio tempo.

Configurável em `application.properties`: `vigil.verificacao.tique`, `vigil.verificacao.tempo-limite`
e `vigil.verificacao.ligado` (desligado nos testes).

## Disponibilidade e quedas

```json
{"nome": "Hanami", "situacao": "NO_AR", "ultimaVerificacao": "2026-10-06T18:41:02Z",
 "ultimas24h": {"disponibilidade": 66.66, "verificacoes": 6, "falhas": 2, "tempoMedioMs": 318, "tempoP95Ms": 336},
 "ultimos7d": {...}, "ultimos30d": {...}}
```

- **Disponibilidade** é a porcentagem de verificações no ar no período, **arredondada para baixo**:
  19 999 de 20 000 aparece como 99,99%, nunca como 100% depois de uma queda.
- **Tempo médio e p95** usam só as verificações no ar (um tempo esgotado de 10 s distorceria a média).
  p95 = 95% das respostas foram mais rápidas que isso.
- **Situação:** `NO_AR` ou `FORA` pela última verificação, `PAUSADO` ou `SEM_DADOS`.
- **Quedas** são verificações seguidas fora do ar, com início, fim (ou `emAndamento`), duração,
  quantas falhas e o motivo da primeira. Saem de uma consulta só, pela técnica de *gaps and
  islands* com `row_number()`. Uma queda que começou antes do período pedido aparece inteira, e a
  de um serviço pausado enquanto estava fora termina na última falha vista.
- As contas são feitas no banco, em SQL puro (`JdbcClient`), numa consulta para todos os serviços.
  O JPA fica para o cadastro.
- O histórico é guardado por 90 dias (`vigil.verificacao.guardar-dias`); uma limpeza roda todo dia às 4h30 de Brasília.

## Organização

```
servico/         Servico (entidade) · ServicoRepository (banco) · ServicoService (regras)
                 ServicoController (rotas HTTP) · ServicoEntrada/ServicoResposta (JSON)
verificacao/     Sonda (acessa a URL e mede) · Verificador (quem verificar, grava o resultado)
                 Agendador (@Scheduled) · Verificacao (entidade) · VerificacaoController (rotas)
disponibilidade/ DisponibilidadeRepository (SQL dos números e das quedas) · DisponibilidadeService
                 DisponibilidadeController · Periodo, Queda, StatusServico (JSON)
erro/            TratadorDeErros (exceções -> respostas HTTP)
```

## Próximos passos

- [x] Esqueleto: Spring Boot, Postgres, Flyway, CI
- [x] Cadastro de serviços (criar, listar, editar, remover) com validação e Swagger
- [x] Verificação periódica com histórico (tempo de resposta, código HTTP)
- [x] Disponibilidade em %, tempo médio e lista de quedas
- [ ] Rotas de administração protegidas (Spring Security), bloqueio de endereços internos (localhost,
      rede privada, 169.254.169.254) e limite de uso do `POST /verificar`
- [ ] Alertas pelo Telegram quando um serviço cai ou volta (só após 2 falhas seguidas: sites no plano
      grátis do Render dormem e a primeira visita passa dos 10 s)
- [x] Apagar verificações antigas (guardadas por 90 dias)
- [ ] Página pública de status e publicação com Docker
