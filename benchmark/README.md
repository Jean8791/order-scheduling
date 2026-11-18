# Teste de carga local

Mede o fluxo completo: o gerador cria N pedidos em `POST /orders` (1.000 requisições simultâneas) e o script espera
até o último pedido sair de `AWAITING_SCHEDULING`. Os serviços rodam como jars em JVMs novas, com `-Xmx` fixo;
MongoDB e RabbitMQ rodam em contêineres descartáveis com limite de CPU e memória.

```bash
./benchmark/run.sh --quick     # 20 mil pedidos; a saída não deve ser commitada
./benchmark/run.sh --label NOME --sizes 100000,1000000 --heaps 256m,512m --reps 3
```

Precisa de Docker, JDK 21 (`BENCH_JAVA_HOME`), Maven, `jq`, `curl` e `python3`. As chaves JWT e a senha do RabbitMQ
são geradas a cada execução, em arquivos 600 fora do repositório. O gerador (`LoadGen.java`) assina o próprio token.

## Cenários

- **spread**: pedidos espalhados por 10 UFs e 14 datas; capacidade de sobra.
- **hotspot**: todos pedem a mesma UF e a mesma data (o caso "todo mundo agenda no mesmo horário"). A janela de busca
  tem 16 janelas de 10.000 vagas (160.000 no total); acima disso, os pedidos são recusados.
- **índices off/on**: aplica por script, por fora da aplicação (ver "Sem DDL" no README), `{state,date}` em
  `deliverySlots` e `{owner,status,createdAt}` em `orders`.

## Colunas

`create_*`: fase de criação (HTTP). `total_s`: do primeiro pedido até o último agendado. `dlq`: mensagens mortas
(deve ser 0). `peak_backlog`: maior fila no RabbitMQ. `rss_*`: pico de memória do processo; `old_*`: pico da old
gen do heap. `mongo_mb`/`rabbit_mb`: pico dos contêineres. `oom`/`timeout`: indicam falha do caso.

## Baseline (código antes das melhorias)

100.000 pedidos, `-Xmx512m`, 1 repetição, 12ª geração i7 (16 núcleos), tudo na mesma máquina:

| Cenário | Índices | Criação | p95 / p99 | Total até o último agendado | Pico da fila | RSS order / scheduling |
|---|---|---|---|---|---|---|
| spread | não | 2.634/s | 725 / 1.204 ms | 293 s | 95.590 | 846 / 441 MB |
| spread | sim | 2.933/s | 643 / 1.105 ms | 277 s | 95.798 | 784 / 423 MB |
| hotspot | não | 2.956/s | 652 / 1.120 ms | 280 s | 95.587 | 816 / 492 MB |
| hotspot | sim | 2.854/s | 691 / 1.143 ms | 284 s | 96.324 | 855 / 456 MB |

Nenhum erro, nenhuma mensagem na DLQ. O que o baseline mostra: a API aceita ~2.800 pedidos/s, mas o agendamento
drena só ~350/s (um consumidor por listener), então a fila chega a ~96% do total. Nesse tamanho os índices não
mudam o resultado (só 42 janelas). Em 100.000 pedidos o hotspot não chega a esgotar as vagas, então o caminho de
recusa só será exercitado com 1 milhão. Resultados completos em `results/baseline-*.csv`.
