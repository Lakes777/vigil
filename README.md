<p align="center"><img src="docs/logo.svg" alt="Logo do Vigil: barras verticais com dois olhos vazados" width="120"></p>

# Vigil

**Vigil · monitor de status dos meus projetos.**

[![CI](https://github.com/Lakes777/vigil/actions/workflows/ci.yml/badge.svg)](https://github.com/Lakes777/vigil/actions/workflows/ci.yml)

API em Java com Spring Boot que verifica de tempos em tempos se os meus sites e APIs estão no ar,
guarda o histórico de cada verificação e calcula a disponibilidade de cada serviço. Quando algo
cai ou volta, avisa pelo Telegram (pelo [Sidekick](https://github.com/Lakes777/bot-utilidades)).

> Em construção. Pronto: cadastro de serviços e verificações periódicas. Próximo: disponibilidade em %.

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

## Organização

```
servico/      Servico (entidade) · ServicoRepository (banco) · ServicoService (regras)
              ServicoController (rotas HTTP) · ServicoEntrada/ServicoResposta (JSON)
verificacao/  Sonda (acessa a URL e mede) · Verificador (quem verificar, grava o resultado)
              Agendador (@Scheduled) · Verificacao (entidade) · VerificacaoController (rotas)
erro/         TratadorDeErros (exceções -> respostas HTTP)
```

## Próximos passos

- [x] Esqueleto: Spring Boot, Postgres, Flyway, CI
- [x] Cadastro de serviços (criar, listar, editar, remover) com validação e Swagger
- [x] Verificação periódica com histórico (tempo de resposta, código HTTP)
- [ ] Disponibilidade em %, tempo médio e lista de quedas
- [ ] Rotas de administração protegidas (Spring Security), bloqueio de endereços internos (localhost,
      rede privada, 169.254.169.254) e limite de uso do `POST /verificar`
- [ ] Alertas pelo Telegram quando um serviço cai ou volta (só após 2 falhas seguidas: sites no plano
      grátis do Render dormem e a primeira visita passa dos 10 s)
- [ ] Apagar verificações antigas (o histórico cresce ~290 linhas por serviço por dia)
- [ ] Página pública de status e publicação com Docker
