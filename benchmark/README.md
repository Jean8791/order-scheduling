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

## Admissão de pedidos (limite de `POST /orders` simultâneos), 5.000 clientes

O cliente do teste espera o `Retry-After` (1 s mais jitter) quando recebe 429 e tenta de novo, como um cliente real
faria. As latências são as do pedido aceito. Um gerador que reenvia na hora gera ~13 mil tentativas por segundo e
atrapalha quem foi aceito, por isso o `LoadGen` passou a esperar.

| Limite (`orders.max-concurrent-creations`) | Criados / falhas | 429 recebidos e repetidos | Tempo de criação | p50 / p95 / p99 do aceito |
|---|---|---|---|---|
| sem limite (antes) | 99.703 / 297 | 0 | 47 s | 2.045 / 4.419 / 6.334 ms |
| 32 | 99.851 / 149 | 169.705 | 57 s | 23 / 123 / 238 ms |
| **64 (padrão)** | 99.493 / 507 | 184.606 | 63 s | 50 / 191 / 593 ms |
| 128 | 99.699 / 301 | 151.584 | 53 s | 94 / 282 / 495 ms |

- Quem é aceito é atendido sem espera (p99 de ~6 s para menos de 600 ms) e o MongoDB deixa de receber uma onda
  de pedidos de uma vez. O custo é o tempo total de criação, 15 a 35% maior, porque cada cliente recusado espera
  1 a 2 s antes de tentar de novo.
- As três configurações ficam na mesma faixa; a diferença entre elas está dentro do ruído de uma execução só.
- Ainda há 0,15% a 0,5% de conexões derrubadas (`received no bytes`) quando 5.000 clientes conectam ao mesmo
  tempo: é a fila de conexões do Tomcat, que a admissão (feita depois que a conexão é aceita) não evita. Um cliente
  real as repetiria.

## 1 milhão de pedidos com admissão, 5.000 clientes simultâneos (hotspot, `-Xmx512m`)

Mesma carga do teste anterior de 1 milhão, mas com 5.000 clientes (em vez de 1.000), limite de 64 criações simultâneas
e clientes que esperam o `Retry-After`.

| Métrica | Sem admissão, 1.000 clientes | Com admissão, 5.000 clientes |
|---|---|---|
| Pedidos criados / perdidos na conexão | 1.000.000 / 0 | 999.444 / 556 (0,06%) |
| 429 recebidos e repetidos | 0 | 1.650.393 |
| Tempo de criação | 387 s (2.582/s) | 509 s (1.965/s) |
| p50 / p95 / p99 do pedido aceito | 378 / 510 / 680 ms | 30 / 97 / 146 ms |
| Do primeiro pedido ao último agendado | 814 s | 946 s |
| Resultado | 160.000 agendados, 840.000 recusados | 160.000 agendados, 839.444 recusados |
| Mensagens na DLQ / pedidos pendentes | 0 / 0 | 0 / 0 |
| Pico da fila no RabbitMQ | 787.278 | 710.507 |
| RSS order / scheduling | 827 / 460 MB | 776 / 488 MB |
| Pico MongoDB / RabbitMQ | 967 / 364 MB | 1.074 / 379 MB |

- Com 5 vezes mais clientes simultâneos, o p99 do pedido aceito ficou 4,6 vezes menor (146 ms contra 680 ms).
- O custo foi o tempo total, 16% maior (946 s contra 814 s). As 556 perdas foram conexões derrubadas
  (`received no bytes`); um cliente real as repetiria. Todo pedido aceito foi agendado ou recusado com motivo.
- Nenhum OOM, nenhuma mensagem na DLQ, memória estável. Tudo no mesmo hardware e com MongoDB limitado a 2 CPUs.

## Outbox (eventos gravados no pedido e publicados por um relay)

100.000 pedidos, 5.000 clientes, hotspot, 512m. O índice esparso `{pendingEvents: 1}` é sempre criado pelo script.

| Versão | Tempo de criação | Total até o último agendado | p50 / p95 / p99 do aceito | Pedidos criados |
|---|---|---|---|---|
| Antes (publicação na requisição) | 63 s | 124 s | 50 / 191 / 593 ms | 99.493 |
| Outbox | 50 s | 118 s | 40 / 162 / 366 ms | 99.702 |

Sem regressão: a publicação saiu do caminho da requisição, e o relay publica em lotes de 500 com confirmação.

### RabbitMQ congelado por 40 s no meio da carga (`--rabbit-pause 40`, 50.000 pedidos, 1.000 clientes)

| Versão | Tempo de criação | Pedidos criados / agendados / pendentes / DLQ |
|---|---|---|
| Antes | 68 s (734/s) | 50.000 / 50.000 / 0 / 0 |
| Outbox | 17,8 s (2.804/s) | 50.000 / 50.000 / 0 / 0 |

O `docker pause` mantém a conexão aberta, então o código antigo também não perdeu evento: ele ficou **parado esperando o
RabbitMQ**, e a API levou 3,8 vezes mais tempo. Com o outbox, a API aceita pedidos normalmente durante a queda e os
eventos saem quando o broker volta. O teste não cobre a perda por queda do próprio serviço entre salvar e publicar;
essa garantia vem do desenho (um documento só) e do `OutboxRepositoryIT`. Os números do código antigo vêm da saída da
execução (o CSV ficou num worktree descartável); os do outbox estão em `results/outbox-*.csv`.

### `kill -9` no order-service no meio da carga (`--kill-order`, 50.000 pedidos, 1.000 clientes)

O script mata o order-service 8 s depois do início da carga e sobe de novo. Como o serviço fica fora do ar por alguns
segundos, a maioria dos clientes recebe erro de conexão: o teste mede o que acontece com os pedidos que chegaram a ser
gravados, não a vazão.

| Versão | Pedidos no banco | Agendados / recusados | **Presos em `AWAITING_SCHEDULING`** | DLQ |
|---|---|---|---|---|
| Antes (publicação na requisição) | 6.832 | 6.795 / 0 | **37** (a execução esgotou o timeout de 300 s esperando) | 0 |
| Outbox, execução 1 | 8.742 | 8.742 / 0 | **0** | 0 |
| Outbox, execução 2 | 7.834 | 7.834 / 0 | **0** | 0 |
| Outbox, execução 3 | 7.953 | 7.953 / 0 | **0** | 0 |

- No código antigo, os pedidos que estavam entre "salvo" e "evento publicado" quando o processo morreu perderam o
  evento: ficam presos para sempre, sem erro e sem DLQ. Com o outbox, o evento estava no documento e o relay o
  publicou depois do reinício.
- O código antigo foi medido numa execução só (o número de presos depende do instante da queda: 37 aqui); o outbox, em
  três, sempre com 0. O CSV do código antigo é `results/kill-antigo-results.csv`, e os do outbox são
  `results/kill-outbox-*.csv`.
- O banco tem mais pedidos do que os clientes viram como criados (`ok`): são pedidos gravados cuja resposta nunca chegou
  porque o processo morreu. Um cliente que repete o `POST` nessa situação cria um pedido duplicado; evitar isso pede uma
  chave de idempotência na API, que não existe.
- Depois do `kill`, as colunas de memória e CPU do order-service deixam de valer (o amostrador segue o processo antigo).

### Cliente que repete o `POST` depois de perder a conexão (`--retry-errors`, com e sem `--idempotency-key`)

O mesmo `kill -9` do order-service, mas agora o cliente repete a requisição quando a conexão falha (50.000 pedidos,
1.000 clientes, hotspot, 512m). Com `--idempotency-key`, cada pedido leva um `Idempotency-Key` fixo em todas as tentativas.

| Cliente | Pedidos que o cliente enviou | Pedidos no banco | Agendados | Presos / DLQ |
|---|---|---|---|---|
| Repete, sem chave | 50.000 | **50.060** | 50.060 | 0 / 0 |
| Repete, com `Idempotency-Key` | 50.000 | **50.000** | 50.000 | 0 / 0 |

Sem a chave, os 60 pedidos que o order-service gravou mas cujo cliente não recebeu a resposta viram **pedidos
duplicados**. Com a chave, a repetição devolve o pedido existente e o banco fica com exatamente um por pedido enviado.
Os CSV estão em `results/dup-*.csv`; `retries_errors` (nas saídas do gerador) foi de ~7.500 repetições por execução.
