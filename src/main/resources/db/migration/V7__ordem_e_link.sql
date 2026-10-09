-- A ordem dos serviços na página (a menor primeiro; empate, pelo nome) e o link que o
-- "Abrir o site" abre. O link fica separado da URL verificada porque às vezes elas são
-- diferentes: o Pursuit é verificado pelo /saude (que testa até o banco), mas quem
-- clica deve cair na página inicial. Sem link, o "Abrir o site" usa a própria URL.
alter table servico add column ordem integer not null default 0;
alter table servico add column link varchar(500);

-- Os serviços que já existem ficam na ordem de hoje (pelo nome)
update servico s set ordem = n.posicao
from (select id, row_number() over (order by nome) as posicao from servico) n
where s.id = n.id;
