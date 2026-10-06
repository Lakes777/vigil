-- Quando cada serviço deve ser verificado de novo (novo serviço: na hora)
alter table servico add column proxima_verificacao timestamptz not null default now();
create index servico_pendentes on servico (proxima_verificacao) where ativo;

-- Histórico: uma linha por verificação. Apagar o serviço apaga o histórico dele.
create table verificacao (
    id           bigserial primary key,
    servico_id   bigint      not null references servico (id) on delete cascade,
    feita_em     timestamptz not null,
    no_ar        boolean     not null,
    codigo_http  integer,               -- vazio quando nem chegou a responder
    tempo_ms     integer     not null,
    erro         varchar(300)
);
create index verificacao_por_servico on verificacao (servico_id, feita_em desc);
