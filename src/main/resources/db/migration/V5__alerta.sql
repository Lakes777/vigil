-- Preenchido enquanto há um aviso de "caiu" mandado e ainda sem o "voltou": guarda desde quando
-- está fora. No banco (e não na memória) para um reinício da API não repetir nem esquecer avisos.
alter table servico add column alerta_fora_desde timestamptz;
