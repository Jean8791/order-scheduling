import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Gerador de carga do benchmark (Java 21, sem dependência: roda com {@code java LoadGen.java}).
 *
 * <pre>
 * slots  --url U --key K --states SP,SC --from 2026-01-01 --days 14 --capacity 10000
 * orders --url U --key K --states SP,SC --from 2026-01-01 --days 14 --total 100000 --concurrency 1000
 * </pre>
 *
 * Em 429 o cliente espera o {@code Retry-After} e tenta de novo (até {@link #MAX_ATTEMPTS} vezes); as latências
 * medidas são as da tentativa que deu certo. Assina o próprio token RS256 com a chave privada de teste (a mesma do par gerado pelo run.sh), então não
 * precisa do auth-service. Ao final do {@code orders}, imprime linhas {@code chave=valor}.
 */
public class LoadGen {

    private static final int MAX_ATTEMPTS = 120;

    public static void main(String[] args) throws Exception {
        Map<String, String> opt = parse(args);
        PrivateKey key = readKey(Path.of(opt.get("key")));
        List<String> states = List.of(opt.get("states").split(","));
        LocalDate from = LocalDate.parse(opt.get("from"));
        int days = Integer.parseInt(opt.get("days"));
        String url = opt.get("url");
        if (args[0].equals("slots")) {
            slots(url, token(key, "ADMIN"), states, from, days, Integer.parseInt(opt.get("capacity")));
        } else {
            orders(url, token(key, "USER"), states, from, days,
                    Integer.parseInt(opt.get("total")), Integer.parseInt(opt.get("concurrency")));
        }
    }

    private static void slots(String url, String token, List<String> states, LocalDate from, int days, int capacity)
            throws Exception {
        HttpClient client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();
        int created = 0;
        for (String state : states) {
            for (int d = 0; d < days; d++) {
                for (String period : List.of("MORNING", "AFTERNOON")) {
                    String body = """
                            {"state":"%s","date":"%s","period":"%s","capacity":%d}""".formatted(state, from.plusDays(d), period, capacity);
                    int status = client.send(request(url + "/delivery-slots", token, body),
                            HttpResponse.BodyHandlers.discarding()).statusCode();
                    if (status != 201) {
                        throw new IllegalStateException("Janela recusada com HTTP " + status);
                    }
                    created++;
                }
            }
        }
        System.out.println("slots=" + created);
    }

    private static void orders(String url, String token, List<String> states, LocalDate from, int days, int total,
                               int concurrency) throws Exception {
        HttpClient client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(10)).build();
        long[] latencyNanos = new long[total];
        AtomicInteger ok = new AtomicInteger();
        AtomicInteger retries429 = new AtomicInteger();
        Map<String, AtomicInteger> failures = new java.util.concurrent.ConcurrentHashMap<>();
        Semaphore inFlight = new Semaphore(concurrency);

        long start = System.nanoTime();
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < total; i++) {
                inFlight.acquire();
                int n = i;
                executor.submit(() -> {
                    ThreadLocalRandom random = ThreadLocalRandom.current();
                    String state = states.get(random.nextInt(states.size()));
                    LocalDate date = from.plusDays(random.nextInt(days));
                    long t0 = System.nanoTime();
                    try {
                        int status = 0;
                        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
                            t0 = System.nanoTime();
                            status = client.send(request(url + "/orders", token, orderBody(state, date)),
                                    HttpResponse.BodyHandlers.discarding()).statusCode();
                            if (status != 429) {
                                break;
                            }
                            // Cliente educado: espera o Retry-After (1 s) mais um pouco de jitter antes de tentar de novo.
                            retries429.incrementAndGet();
                            Thread.sleep(1000 + random.nextInt(1000));
                        }
                        if (status == 201) {
                            ok.incrementAndGet();
                        } else {
                            failures.computeIfAbsent("http_" + status, k -> new AtomicInteger()).incrementAndGet();
                        }
                    } catch (IOException e) {
                        failures.computeIfAbsent("error_" + e.getClass().getSimpleName() + "_" + String.valueOf(e.getMessage()).replaceAll("[^A-Za-z0-9 ]", "").replace(" ", "-"), k -> new AtomicInteger()).incrementAndGet();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        latencyNanos[n] = System.nanoTime() - t0;
                        inFlight.release();
                    }
                });
            }
        }
        double seconds = (System.nanoTime() - start) / 1e9;

        Arrays.sort(latencyNanos);
        System.out.println("sent=" + total);
        System.out.println("ok=" + ok.get());
        System.out.println("failed=" + (total - ok.get()));
        System.out.println("retries_429=" + retries429.get());
        new TreeMap<>(failures).forEach((k, v) -> System.out.println(k + "=" + v.get()));
        System.out.printf("create_seconds=%.1f%n", seconds);
        System.out.printf("create_rps=%.0f%n", total / seconds);
        System.out.printf("p50_ms=%.0f%n", percentile(latencyNanos, 0.50));
        System.out.printf("p95_ms=%.0f%n", percentile(latencyNanos, 0.95));
        System.out.printf("p99_ms=%.0f%n", percentile(latencyNanos, 0.99));
    }

    private static double percentile(long[] sortedNanos, double p) {
        return sortedNanos[(int) Math.min(sortedNanos.length - 1, Math.floor(p * sortedNanos.length))] / 1e6;
    }

    private static String orderBody(String state, LocalDate date) {
        return """
                {"customer":"Cliente de teste","address":{"street":"Rua A","number":"1","city":"Cidade","state":"%s","zipCode":"01001000"},\
                "items":[{"sku":"SKU-1","description":"Item de teste","quantity":1}],"desiredDeliveryDate":"%s"}"""
                .formatted(state, date);
    }

    private static HttpRequest request(String url, String token, String body) {
        return HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(60))
                .header("Content-Type", "application/json").header("Authorization", "Bearer " + token)
                .POST(HttpRequest.BodyPublishers.ofString(body)).build();
    }

    private static String token(PrivateKey key, String role) throws Exception {
        long now = System.currentTimeMillis() / 1000;
        String header = b64("{\"alg\":\"RS256\",\"typ\":\"JWT\"}");
        String claims = b64("{\"iss\":\"auth-service\",\"sub\":\"benchmark@example.com\",\"roles\":[\"" + role
                + "\"],\"iat\":" + now + ",\"exp\":" + (now + 6 * 3600) + "}");
        Signature signature = Signature.getInstance("SHA256withRSA");
        signature.initSign(key);
        signature.update((header + "." + claims).getBytes(StandardCharsets.US_ASCII));
        return header + "." + claims + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(signature.sign());
    }

    private static String b64(String json) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }

    private static PrivateKey readKey(Path pem) throws Exception {
        String base64 = Files.readString(pem).replaceAll("-----[A-Z ]+-----", "").replaceAll("\\s", "");
        return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(base64)));
    }

    private static Map<String, String> parse(String[] args) {
        Map<String, String> options = new java.util.HashMap<>();
        for (int i = 1; i < args.length; i += 2) {
            options.put(args[i].substring(2), args[i + 1]);
        }
        return options;
    }
}
