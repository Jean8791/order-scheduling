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

## Melhorias medidas (100.000 pedidos, `-Xmx512m`, sem índices)

| Versão | Cenário | Total até o último agendado | Operações no Mongo por pedido | Pico da fila |
|---|---|---|---|---|
| Baseline | spread / hotspot | 293 s / 280 s | 8 | 95.590 / 95.587 |
| 1. Listeners com 4 a 8 consumidores e prefetch 50 | spread / hotspot | 147 s / 139 s | 8 | 85.013 / 82.823 |
| 2. Agendamento com um único insert (sem `PROCESSING`) | spread / hotspot | 125 s / 115 s | 6 | 77.381 / 76.176 |

- A varredura de concorrência (4, 8 e 16 consumidores, 50 mil pedidos) deu o mesmo resultado nos três valores
  (79 a 89 s): o limite deixou de ser o número de consumidores e passou a ser o MongoDB, que chegou a ~245% de CPU
  com o limite de 2 CPUs do contêiner. Por isso o passo 2 reduziu as operações por pedido.
- Os resultados de cada passo estão em `results/step1-*.csv` e `results/step2-*.csv`. As colunas de CPU e de
  operações por pedido só existem a partir do passo 1 (o baseline não as tem).

## 1 milhão de pedidos (versão com os passos 1 e 2, hotspot, `-Xmx512m`, 1.000 clientes simultâneos)

| Métrica | Resultado |
|---|---|
| Pedidos criados / falhas de HTTP / mensagens na DLQ | 1.000.000 / 0 / 0 |
| Criação | 387 s (2.582 pedidos/s), p50 378 ms, p95 510 ms, p99 680 ms |
| Do primeiro pedido ao último agendado | 814 s (13,6 min) |
| Resultado | 160.000 `SCHEDULED` (as vagas do hotspot) e 840.000 `NOT_SCHEDULED` com motivo |
| Pico da fila no RabbitMQ | 787.278 mensagens |
| Pico de memória (RSS) | order-service 827 MB, scheduling-service 460 MB (heap de 512m) |
| Pico dos contêineres | MongoDB 967 MB, RabbitMQ 364 MB |
| Operações no Mongo por pedido | 5,1 (a recusa não reserva vaga) |

Nenhum OOM e nenhum serviço caiu. O RabbitMQ segurou quase 800 mil mensagens com só 364 MB (as mensagens vão para o
disco). Com o baseline, o mesmo volume levaria cerca de 48 minutos só para o agendamento, pela taxa medida em 100 mil.

## Mais clientes simultâneos (100 mil pedidos, hotspot, 5.000 clientes)

| Configuração do order-service | Criados / falhas | Vazão | p50 / p95 / p99 |
|---|---|---|---|
| Padrão | 99.703 / 297 | 2.110/s | 2.045 / 4.419 / 6.334 ms |
| `server.tomcat.max-keep-alive-requests=-1` | 99.788 / 212 | 2.157/s | 1.981 / 4.425 / 5.436 ms |
| `spring.threads.virtual.enabled=true` | 641 / 99.359 | n/d | n/d (o serviço deixou de aceitar conexões) |

A vazão não sobe com mais clientes: a espera vira latência (p99 de 6 s) e cerca de 0,3% das conexões são
recusadas (`received no bytes`). Desligar o limite de requisições por conexão não resolveu, e as threads virtuais
pioraram muito, porque nada limita o que entra no MongoDB. Quem decide se o sistema aguenta é a admissão de
pedidos (recusar rápido com 429/503 quando está cheio), que ainda não foi implementada.
