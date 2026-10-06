-- Servicos que o Vigil monitora
create table servico (
    id                  bigserial primary key,
    nome                varchar(80)  not null unique,
    url                 varchar(500) not null,
    intervalo_segundos  integer      not null default 300
                        check (intervalo_segundos between 60 and 86400),
    ativo               boolean      not null default true,
    criado_em           timestamptz  not null default now()
);
