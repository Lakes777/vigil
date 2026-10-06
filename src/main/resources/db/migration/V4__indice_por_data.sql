-- Para as contas de disponibilidade de todos os serviços (últimos 30 dias) e a limpeza diária
create index verificacao_por_data on verificacao (feita_em);
