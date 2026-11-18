#!/usr/bin/env bash
# Teste de carga local do order-scheduling: cria N pedidos pela API e mede até o último ser agendado.
#   ./benchmark/run.sh --quick                      teste rápido (20 mil pedidos); a saída não deve ser commitada
#   ./benchmark/run.sh                              grade padrão: 100 mil pedidos, heap 512m, 2 cenários, sem e com índices
#   ./benchmark/run.sh --sizes 100000,1000000 --heaps 256m,512m --reps 3     grade maior (lento)
#   Opções: --sizes --heaps --scenarios (spread,hotspot) --indexes (off,on) --reps --concurrency (clientes simultâneos, padrão 1000) --label NOME
# Precisa de: Docker, JDK 21 (BENCH_JAVA_HOME; padrão /usr/lib/jvm/java-21-openjdk-amd64), Maven, jq, curl, python3.
# Limites dos contêineres (por fora dos serviços): BENCH_MONGO_CPUS/MEM e BENCH_RABBIT_CPUS/MEM (padrão 2 e 2g).
set -euo pipefail
export LC_ALL=C

cd "$(dirname "$0")"
root="$(cd .. && pwd)"
java_home="${BENCH_JAVA_HOME:-/usr/lib/jvm/java-21-openjdk-amd64}"
java="$java_home/bin/java"

csv_list() { IFS=, read -r -a "$1" <<<"$2"; }
concurrency=1000; sizes=(100000); heaps=(512m); scenarios=(spread hotspot); indexes=(off on); reps=1; prefix=""
while [ $# -gt 0 ]; do
  case "$1" in
    --quick) sizes=(20000); scenarios=(spread hotspot); indexes=(off); reps=1; prefix="quick-" ;;
    --sizes) csv_list sizes "$2"; shift ;;
    --heaps) csv_list heaps "$2"; shift ;;
    --scenarios) csv_list scenarios "$2"; shift ;;
    --indexes) csv_list indexes "$2"; shift ;;
    --reps) reps="$2"; shift ;;
    --concurrency) concurrency="$2"; shift ;;
    --label) prefix="$2-"; shift ;;
    *) echo "opção desconhecida: $1" >&2; exit 1 ;;
  esac
  shift
done

results="results/${prefix}results.csv"
summary="results/${prefix}summary.csv"
environment="results/${prefix}environment.txt"
mkdir -p results

workdir="$(mktemp -d)"
mongo="bench-mongo-$$"
rabbit="bench-rabbit-$$"
pids=()
cleanup() {
  [ -n "${BENCH_KEEP_LOGS:-}" ] && cp "$workdir"/*.log "$BENCH_KEEP_LOGS"/ 2>/dev/null || true
  for pid in "${pids[@]}"; do kill "$pid" 2>/dev/null || true; done
  docker rm -f "$mongo" "$rabbit" >/dev/null 2>&1 || true
  rm -rf "$workdir"
}
trap cleanup EXIT

echo "▶ compilando os serviços (JDK 21)"
(cd "$root" && JAVA_HOME="$java_home" mvn -q -ntp -DskipTests package -pl order-service,scheduling-service)
order_jar="$(ls "$root"/order-service/target/order-service-*.jar | head -1)"
sched_jar="$(ls "$root"/scheduling-service/target/scheduling-service-*.jar | head -1)"

echo "▶ gerando o par de chaves de teste (fora do repositório)"
( umask 177; openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out "$workdir/private.pem" 2>/dev/null )
openssl pkey -in "$workdir/private.pem" -pubout -out "$workdir/public.pem"

echo "▶ subindo MongoDB e RabbitMQ em contêineres com limite de CPU e memória"
rabbit_pass="$(openssl rand -hex 16)"
( umask 177
  printf 'RABBITMQ_DEFAULT_USER=bench\nRABBITMQ_DEFAULT_PASS=%s\n' "$rabbit_pass" > "$workdir/rabbit.env"
  printf 'user = "bench:%s"\n' "$rabbit_pass" > "$workdir/curl.cfg" )
docker run -d --name "$mongo" --cpus "${BENCH_MONGO_CPUS:-2}" --memory "${BENCH_MONGO_MEM:-2g}" \
  -p 127.0.0.1::27017 mongo:7.0 >/dev/null
docker run -d --name "$rabbit" --cpus "${BENCH_RABBIT_CPUS:-2}" --memory "${BENCH_RABBIT_MEM:-2g}" \
  --env-file "$workdir/rabbit.env" -p 127.0.0.1::5672 -p 127.0.0.1::15672 rabbitmq:3.13-management >/dev/null
mongo_port="$(docker port "$mongo" 27017/tcp | head -1 | sed 's/.*://')"
amqp_port="$(docker port "$rabbit" 5672/tcp | head -1 | sed 's/.*://')"
mgmt_port="$(docker port "$rabbit" 15672/tcp | head -1 | sed 's/.*://')"
until docker exec "$mongo" mongosh --quiet --eval 'db.adminCommand("ping").ok' >/dev/null 2>&1; do sleep 1; done
until docker exec "$rabbit" rabbitmq-diagnostics -q ping >/dev/null 2>&1; do sleep 1; done
until curl -sf --config "$workdir/curl.cfg" "http://127.0.0.1:$mgmt_port/api/overview" >/dev/null; do sleep 1; done

mongo_ops() { # operações de dados executadas no MongoDB desde que subiu
  mongo_eval 'const c = db.serverStatus().metrics.commands; print(["find","insert","update","findAndModify","delete"].reduce((a, k) => a + (c[k] ? Number(c[k].total) : 0), 0))'
}
mongo_eval() { docker exec "$mongo" mongosh --quiet --eval "$1"; }
rabbit_api() { curl -sf --config "$workdir/curl.cfg" "$@"; }
queues() { rabbit_api "http://127.0.0.1:$mgmt_port/api/queues/%2F"; }
backlog() { queues | jq '[.[] | select(.name | endswith(".dlq") | not) | (.messages // 0)] | add // 0'; }
dlq_total() { queues | jq '[.[] | select(.name | endswith(".dlq")) | (.messages // 0)] | add // 0'; }

wait_health() {
  for _ in $(seq 1 120); do
    curl -sf "http://127.0.0.1:$1/actuator/health" >/dev/null 2>&1 && return 0
    sleep 1
  done
  echo "serviço na porta $1 não subiu" >&2; return 1
}

start_service() { # nome jar porta banco heap
  env SPRING_DATA_MONGODB_URI="mongodb://127.0.0.1:$mongo_port/$4" \
      RABBITMQ_URL="amqp://bench:$rabbit_pass@127.0.0.1:$amqp_port" \
      JWT_PUBLIC_KEY_LOCATION="file:$workdir/public.pem" PORT="$3" \
      "$java" "-Xmx$5" -XX:+ExitOnOutOfMemoryError -jar "$2" >"$workdir/$1.log" 2>&1 &
  pids+=("$!")
  started_pid="$!"
}

mb() { awk -v kb="$1" 'BEGIN { printf "%.0f", kb / 1024 }'; }
rss_kb() { awk '/VmRSS/ {print $2}' "/proc/$1/status" 2>/dev/null || echo 0; }
old_kb() { "$java_home/bin/jstat" -gc "$1" 2>/dev/null | awk 'NR==1 {for (i=1;i<=NF;i++) if ($i=="OU") c=i} NR==2 {print int($c)}' || echo 0; }
ticks() { awk '{print $14 + $15}' "/proc/$1/stat" 2>/dev/null || echo 0; } # CPU do processo, em ticks (100/s)
container_stats() { # "mem_mb cpu_pct" de cada contêiner (mongo, rabbit), numa chamada só
  docker stats --no-stream --format '{{.Name}} {{.MemUsage}} {{.CPUPerc}}' "$mongo" "$rabbit" | sort |
    awk '{v=$2; n=v+0; if (v ~ /GiB/) n*=1024; else if (v ~ /KiB/) n/=1024; printf "%.0f %.0f ", n, $5+0}'
}

sampler() { # arquivo pid_order pid_sched
  local stats
  while true; do
    stats="$(container_stats)" # ordem: mongo, rabbit (por nome: bench-mongo < bench-rabbit)
    set -- "$1" "$2" "$3" $stats
    printf '%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s\n' "$(date +%s.%N)" "$(mb "$(rss_kb "$2")")" "$(mb "$(rss_kb "$3")")" \
      "$(mb "$(old_kb "$2")")" "$(mb "$(old_kb "$3")")" "$(backlog 2>/dev/null || echo 0)" \
      "$4" "$6" "$(dlq_total 2>/dev/null || echo 0)" "$5" "$7" "$(ticks "$2")" "$(ticks "$3")" >> "$1"
    sleep 1
  done
}

cpu_peak() { # arquivo coluna_de_ticks -> pico de CPU (% de um núcleo) entre amostras consecutivas
  awk -F, -v c="$2" 'NR>1 && $1>t {p=($c-k)/($1-t); if (p>m) m=p} {t=$1; k=$c} END {printf "%.0f", m}' "$1"
}

peak() { awk -F, -v c="$2" 'NR>0 && $c+0>m {m=$c+0} END {print m+0}' "$1"; }

{
  echo "date_utc: $(date -u +%Y-%m-%dT%H:%MZ)"
  echo "cpu: $(lscpu | awk -F: '/Model name/ {gsub(/^ +/,"",$2); print $2}') ($(nproc) logical cores)"
  echo "ram: $(free -h | awk '/Mem/ {print $2}') total"
  echo "os: $(uname -sr)"
  echo "java: $("$java" -version 2>&1 | head -1)"
  echo "mongodb: mongo:7.0 (limite: ${BENCH_MONGO_CPUS:-2} CPUs, ${BENCH_MONGO_MEM:-2g})"
  echo "rabbitmq: rabbitmq:3.13-management (limite: ${BENCH_RABBIT_CPUS:-2} CPUs, ${BENCH_RABBIT_MEM:-2g})"
  echo "docker: $(docker --version)"
  echo "commit: $(git -C "$root" rev-parse --short HEAD) (+ alterações locais, se houver)"
  echo "jvm_flags: -Xmx por caso; GC padrão (G1); -XX:+ExitOnOutOfMemoryError"
  echo "observação: gerador de carga, serviços, MongoDB e RabbitMQ rodam na mesma máquina; use os números para comparar versões"
} > "$environment"

echo "scenario,size,heap,indexes,rep,ok,failed,create_s,create_rps,p50_ms,p95_ms,p99_ms,total_s,scheduled,not_scheduled,pending,dlq,peak_backlog,rss_order_mb,rss_sched_mb,old_order_mb,old_sched_mb,mongo_mb,rabbit_mb,cpu_order_pct,cpu_sched_pct,cpu_mongo_pct,cpu_rabbit_pct,mongo_ops_per_order,oom,timeout" > "$results"

run_case() { # cenário tamanho heap índices repetição
  local scenario="$1" size="$2" heap="$3" idx="$4" rep="$5"
  echo "▶ cenário=$scenario pedidos=$size heap=$heap índices=$idx rep=$rep"

  mongo_eval 'db.getSiblingDB("orders").dropDatabase(); db.getSiblingDB("scheduling").dropDatabase()' >/dev/null
  for q in $(queues | jq -r '.[].name'); do rabbit_api -X DELETE "http://127.0.0.1:$mgmt_port/api/queues/%2F/$q" >/dev/null; done
  if [ "$idx" = on ]; then
    mongo_eval 'db.getSiblingDB("scheduling").deliverySlots.createIndex({state:1,date:1});
                db.getSiblingDB("orders").orders.createIndex({owner:1,status:1,createdAt:-1})' >/dev/null
  fi

  local sched_pid order_pid
  start_service scheduling "$sched_jar" 18082 scheduling "$heap"; sched_pid="$started_pid"
  start_service order "$order_jar" 18081 orders "$heap"; order_pid="$started_pid"
  wait_health 18082; wait_health 18081

  local from states order_days
  from="$(TZ=America/Sao_Paulo date -d tomorrow +%F)"
  if [ "$scenario" = spread ]; then states="SP,SC,PR,RJ,MG,RS,BA,GO,PE,CE"; order_days=14; else states="SP"; order_days=1; fi
  "$java" -Duser.language=en -Duser.country=US LoadGen.java slots --url http://127.0.0.1:18082 --key "$workdir/private.pem" \
    --states "$states" --from "$from" --days 21 --capacity 10000 >/dev/null

  local samples="$workdir/samples.csv"; : > "$samples"
  sampler "$samples" "$order_pid" "$sched_pid" & local sampler_pid=$!; pids+=("$sampler_pid")

  local t0 loadgen="$workdir/loadgen.out" ops0
  ops0="$(mongo_ops)"
  t0="$(date +%s.%N)"
  "$java" -Duser.language=en -Duser.country=US LoadGen.java orders --url http://127.0.0.1:18081 --key "$workdir/private.pem" \
    --states "$states" --from "$from" --days "$order_days" --total "$size" --concurrency "$concurrency" > "$loadgen"
  local ok; ok="$(awk -F= '/^ok=/ {print $2}' "$loadgen")"

  local timeout_s="${BENCH_TIMEOUT:-3600}" zeros=0 timed_out=no pending="$ok" oom=no
  while [ "$pending" -gt 0 ]; do
    if [ "$(echo "$(date +%s.%N) - $t0 > $timeout_s" | bc)" = 1 ]; then timed_out=yes; break; fi
    if ! kill -0 "$order_pid" 2>/dev/null || ! kill -0 "$sched_pid" 2>/dev/null; then oom=yes; break; fi
    if [ "$(backlog)" = 0 ]; then zeros=$((zeros + 1)); else zeros=0; fi
    if [ "$zeros" -ge 3 ]; then
      pending="$(mongo_eval 'db.getSiblingDB("orders").orders.countDocuments({status:"AWAITING_SCHEDULING"})')"
      zeros=0
      [ "$pending" -gt 0 ] && [ "$(dlq_total)" -gt 0 ] && break
    fi
    sleep 2
  done
  local t1 ops_per_order; t1="$(date +%s.%N)"
  ops_per_order="$(echo "scale=1; ($(mongo_ops) - $ops0) / $ok" | bc)"
  grep -q OutOfMemoryError "$workdir/order.log" "$workdir/scheduling.log" 2>/dev/null && oom=yes
  sleep 2

  local counts scheduled not_scheduled
  counts="$(mongo_eval 'JSON.stringify(db.getSiblingDB("orders").orders.aggregate([{$group:{_id:"$status",n:{$sum:1}}}]).toArray())')"
  scheduled="$(echo "$counts" | jq '[.[] | select(._id=="SCHEDULED") | .n] | add // 0')"
  not_scheduled="$(echo "$counts" | jq '[.[] | select(._id=="NOT_SCHEDULED") | .n] | add // 0')"
  pending="$(echo "$counts" | jq '[.[] | select(._id=="AWAITING_SCHEDULING") | .n] | add // 0')"

  kill "$sampler_pid" 2>/dev/null || true
  kill "$order_pid" "$sched_pid" 2>/dev/null || true
  wait "$order_pid" "$sched_pid" 2>/dev/null || true

  field() { awk -F= -v k="$1" '$1==k {print $2}' "$loadgen"; }
  printf '%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%.0f,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s\n' \
    "$scenario" "$size" "$heap" "$idx" "$rep" "$ok" "$(field failed)" "$(field create_seconds)" "$(field create_rps)" \
    "$(field p50_ms)" "$(field p95_ms)" "$(field p99_ms)" "$(echo "$t1 - $t0" | bc)" \
    "$scheduled" "$not_scheduled" "$pending" "$(tail -1 "$samples" | cut -d, -f9)" "$(peak "$samples" 6)" \
    "$(peak "$samples" 2)" "$(peak "$samples" 3)" "$(peak "$samples" 4)" "$(peak "$samples" 5)" \
    "$(peak "$samples" 7)" "$(peak "$samples" 8)" "$(cpu_peak "$samples" 12)" "$(cpu_peak "$samples" 13)" \
    "$(peak "$samples" 10)" "$(peak "$samples" 11)" "$ops_per_order" "$oom" "$timed_out" >> "$results"
  cp "$loadgen" "results/${prefix}last-loadgen.txt"
  tail -1 "$results"
}

for scenario in "${scenarios[@]}"; do for size in "${sizes[@]}"; do for heap in "${heaps[@]}"; do
  for idx in "${indexes[@]}"; do for rep in $(seq 1 "$reps"); do
    run_case "$scenario" "$size" "$heap" "$idx" "$rep"
  done; done
done; done; done

python3 -I - "$results" "$summary" <<'PY'
import csv, statistics, sys
rows = list(csv.DictReader(open(sys.argv[1])))
keys = ("scenario", "size", "heap", "indexes")
numeric = [c for c in rows[0] if c not in keys + ("rep", "oom", "timeout")]
groups = {}
for r in rows:
    groups.setdefault(tuple(r[k] for k in keys), []).append(r)
with open(sys.argv[2], "w", newline="") as f:
    w = csv.writer(f)
    w.writerow(list(keys) + ["reps"] + numeric + ["oom", "timeout"])
    for k, g in groups.items():
        med = [statistics.median(float(r[c] or 0) for r in g) for c in numeric]
        w.writerow(list(k) + [len(g)] + med
                   + ["yes" if any(r["oom"] == "yes" for r in g) else "no",
                      "yes" if any(r["timeout"] == "yes" for r in g) else "no"])
PY
echo "✔ resultados em $results e $summary"
