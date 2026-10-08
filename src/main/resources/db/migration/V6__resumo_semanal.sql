-- As semanas cujo resumo já foi mandado, pela segunda-feira que abre a semana. O agendador tenta
-- de hora em hora: com a marca no banco, um reinício da API ou várias tentativas na mesma semana
-- nunca mandam o mesmo resumo duas vezes (a chave primária garante, mesmo com duas ao mesmo tempo).
create table resumo_semanal (
    semana date primary key,
    enviado_em timestamptz not null default now()
);
