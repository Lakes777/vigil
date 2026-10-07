<p align="center"><img src="docs/logo.svg" alt="Logo do Vigil: barras verticais com dois olhos vazados" width="120"></p>

# Vigil

**Vigil · monitor de status dos meus projetos.**

[![CI](https://github.com/Lakes777/vigil/actions/workflows/ci.yml/badge.svg)](https://github.com/Lakes777/vigil/actions/workflows/ci.yml)

API em Java com Spring Boot que verifica de tempos em tempos se os meus sites e APIs estão no ar,
guarda o histórico de cada verificação e calcula a disponibilidade de cada serviço. Quando algo
cai ou volta, avisa pelo Telegram (pelo [Sidekick](https://github.com/Lakes777/bot-utilidades)).

> Em construção. Pronto: cadastro, verificações, disponibilidade, segurança e avisos pelo Telegram. Próximo: página de status e publicação.

## Tecnologias

- Java 21 e Spring Boot 4 (Web MVC, Data JPA, Validation, Security, Actuator)
- Swagger / OpenAPI (springdoc)
- PostgreSQL com migrações pelo Flyway
- Testes com JUnit 5, Mockito, Testcontainers (Postgres de verdade num contêiner) e WireMock (sites
  falsos: lentos, fora do ar, com erro, redirecionando para a rede interna)
- GitHub Actions

## Como rodar

Precisa do Java 21 e do Docker.

```bash
# Uma vez só: a chave de admin, num arquivo que fica fora do git
mkdir -p config && echo "vigil.admin.chave=$(openssl rand -hex 32)" > config/application.properties

docker compose up -d     # sobe o Postgres na porta 5433
./mvnw spring-boot:run   # API em http://localhost:8080
```

Sem a chave (ou com menos de 32 caracteres) a API nem sobe. Em produção ela vem da variável de
ambiente `VIGIL_ADMIN_CHAVE`.

Para receber os avisos no Telegram, acrescente no mesmo arquivo o token do bot e o seu ID
(em produção: `TELEGRAM_TOKEN` e `TELEGRAM_CHAT_ID`). Sem eles, os avisos vão só para o log.

```properties
vigil.alerta.telegram.token=123456:ABC...
vigil.alerta.telegram.chat=123456789
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

Ler (`GET`) é público. Cadastrar, editar, remover e "verificar agora" pedem a chave de admin:

```bash
curl -X POST localhost:8080/api/servicos -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $CHAVE" \
  -d '{"nome": "Coursebook", "url": "https://painel-estudos-cyan.vercel.app"}'
```

No Swagger, a chave vai no botão **Authorize**.

Erros seguem o formato padrão *problem detail* (RFC 9457), com a mensagem de cada campo inválido:

```json
{"status": 400, "title": "Dados inválidos", "detail": "Confira os campos indicados.",
 "campos": {"url": "use uma URL que comece com http:// ou https://"}}
```

O nome é único sem diferenciar maiúsculas ("Encore" e "encore" são o mesmo), garantido também
por um índice no banco.

## Segurança

- **Chave de admin** (Spring Security): `GET` é público, porque a página de status vai mostrar
  esses dados. `POST`, `PUT` e `DELETE` pedem `Authorization: Bearer <chave>`. Sem a chave: 401
  "Chave de admin necessária"; com a chave errada: 401 "Chave inválida" (até num `GET`: melhor
  avisar do que ignorar em silêncio). A comparação leva o
  mesmo tempo acerte ou erre (`MessageDigest.isEqual`), para a chave não poder ser descoberta
  aos poucos medindo o tempo da resposta. Sem sessão nem cookie, então a proteção contra CSRF
  não se aplica.
- **SSRF:** o Vigil acessa a URL que for cadastrada, então poderia ser usado para enxergar o que
  só existe por dentro do servidor (o banco, a rede privada, o `169.254.169.254`, de onde as
  nuvens entregam as credenciais da máquina). O `FiltroDeEnderecos` bloqueia localhost, redes
  privadas, link-local, `100.64.0.0/10`, multicast, reservados e os equivalentes em IPv6,
  inclusive um IPv4 escondido dentro de um IPv6 (`::ffff:127.0.0.1`).
  - No cadastro, recusa o que já se vê na URL (`localhost`, `http://10.0.0.5`, até `http://2130706433`).
  - Antes de **cada** acesso, consulta o DNS e confere todos os IPs do domínio, porque ele pode
    passar a apontar para um IP interno depois do cadastro. O Java guarda essa resposta do DNS por
    30 s (fixado no `main`) e a conexão, logo em seguida, quase sempre usa a mesma. Isso reduz
    muito a chance de o domínio responder um IP para o filtro e outro para a conexão (*DNS
    rebinding*); sobra uma janela pequena, se o cache vencer bem entre os dois. Fechar de vez
    exigiria conectar direto no IP conferido, o que o `HttpClient` do Java não permite.
  - Os **redirecionamentos são seguidos um a um** pela própria Sonda (no máximo 5), e cada salto
    passa pelo filtro: um site público poderia redirecionar para `http://127.0.0.1`. O tempo-limite
    vale para o caminho todo (a consulta ao DNS fica de fora: o Java não tem limite para ela).
- **Limite do "verificar agora":** uma vez a cada 10 s por serviço
  (`vigil.verificacao.espera-manual`). Antes disso, 429 com o cabeçalho `Retry-After`. Assim um
  script em laço não faz o Vigil disparar pedidos sem parar contra um site.

## Como as verificações funcionam

A cada 10 segundos (o "tique") o `Agendador` pede ao `Verificador` os serviços ativos cuja hora
de verificar chegou. Cada um é acessado pela `Sonda` numa **thread virtual** do Java 21, todos em
paralelo, então um site lento não atrasa os outros. O resultado vai para a tabela `verificacao`,
e a próxima verificação fica marcada para daqui a `intervaloSegundos`.

```json
{"feitaEm": "2026-10-06T18:07:41Z", "noAr": false, "codigoHttp": null, "tempoMs": 10004,
 "erro": "tempo esgotado (10 s)"}
```

- **No ar:** respondeu com código 2xx ou 3xx (redirecionamentos são seguidos, até 5).
- **Fora:** código 4xx/5xx, tempo esgotado (10 s), conexão recusada, domínio inexistente,
  erro de rede ou endereço interno bloqueado.
- As chamadas HTTP ficam **fora de transação**: esperar um site lento não segura uma conexão do banco.
- Só os cabeçalhos da resposta são lidos: um site que mandasse o conteúdo devagar não prende a verificação.
- Editar um serviço o coloca para verificar na hora (a URL pode ter mudado), mesmo que uma verificação
  esteja em andamento: ela só agenda a próxima se ninguém editou o serviço no meio tempo.

Configurável em `application.properties`: `vigil.verificacao.tique`, `vigil.verificacao.tempo-limite`
e `vigil.verificacao.ligado` (desligado nos testes).

## Avisos pelo Telegram

O Vigil manda a mensagem pela API do Telegram com o token do
[Sidekick](https://github.com/Lakes777/bot-utilidades): o aviso chega na conversa com o bot, sem
mudar nada nele.

```
Vigil: Hanami caiu (tempo esgotado (10 s)). Fora desde 14:32 de 06/10.
Vigil: Hanami voltou. Ficou fora por 12 min (desde 14:32 de 06/10).
```

- **Caiu** só depois de **2 falhas seguidas** (`vigil.alerta.falhas-seguidas`). Sites no plano grátis
  do Render dormem e a primeira visita passa dos 10 s: com um aviso por falha, o celular tocaria à toa.
  "Seguidas" quer dizer sem buraco (no máximo dois intervalos entre uma e outra): uma falha de antes
  de a API ficar desligada não se soma a uma de agora.
- **Voltou** na primeira verificação no ar depois de um "caiu". Enquanto continua fora, não repete.
  O tempo fora conta do começo da queda até a volta, inclusive um período com a API desligada.
- **Pausar ou trocar a URL** fecha o aviso sem mandar "voltou" (a queda antiga deixa de valer).
- O estado fica no banco (`servico.alerta_fora_desde`), então reiniciar a API não repete nem esquece
  avisos. Abrir e fechar o aviso são `update ... where` condicionais: se a verificação manual e a do
  agendador terminarem juntas, só uma manda a mensagem.
- Se o Telegram não responder, o aviso é desfeito e a próxima verificação tenta de novo.
- Um problema nos avisos nunca derruba a verificação: vai só para o log.
- O token só é usado se tiver o formato do BotFather, e nunca aparece no log.
- Nos testes, o Telegram é um WireMock; o de verdade nunca é chamado.

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
                 LimiteManual (uma verificação manual a cada 10 s)
disponibilidade/ DisponibilidadeRepository (SQL dos números e das quedas) · DisponibilidadeService
                 DisponibilidadeController · Periodo, Queda, StatusServico (JSON)
alerta/          Alertas (quando avisar) · AlertaRepository (estado no banco) · Telegram (envio)
seguranca/       SegurancaConfig (quem pode o quê) · FiltroDaChave (confere a chave)
                 FiltroDeEnderecos (bloqueia a rede interna: SSRF)
erro/            TratadorDeErros (exceções -> respostas HTTP)
```

## Próximos passos

- [x] Esqueleto: Spring Boot, Postgres, Flyway, CI
- [x] Cadastro de serviços (criar, listar, editar, remover) com validação e Swagger
- [x] Verificação periódica com histórico (tempo de resposta, código HTTP)
- [x] Disponibilidade em %, tempo médio e lista de quedas
- [x] Rotas de administração protegidas (Spring Security), bloqueio de endereços internos (localhost,
      rede privada, 169.254.169.254) e limite de uso do `POST /verificar`
- [x] Alertas pelo Telegram quando um serviço cai ou volta (só após 2 falhas seguidas: sites no plano
      grátis do Render dormem e a primeira visita passa dos 10 s)
- [x] Apagar verificações antigas (guardadas por 90 dias)
- [ ] Página pública de status e publicação com Docker
