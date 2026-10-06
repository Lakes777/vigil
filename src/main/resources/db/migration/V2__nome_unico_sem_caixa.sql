-- "Encore" e "encore" passam a contar como o mesmo nome
alter table servico drop constraint servico_nome_key;
create unique index servico_nome_unico on servico (lower(nome));
