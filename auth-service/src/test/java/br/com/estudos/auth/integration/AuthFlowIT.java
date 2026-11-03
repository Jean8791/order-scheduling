package br.com.estudos.auth.integration;

import br.com.estudos.auth.support.IntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;

import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class AuthFlowIT extends IntegrationTest {

    private static final String PASSWORD = "senha-forte";

    @Autowired
    MongoTemplate mongo;

    @Test
    void registerLoginAndMeWithTheIssuedToken() {
        String email = uniqueEmail();

        ResponseEntity<JsonNode> registered = post("/auth/register", user("Ana Souza", email));
        ResponseEntity<JsonNode> login = post("/auth/login", Map.of("email", email.toUpperCase(), "password", PASSWORD));
        ResponseEntity<JsonNode> me = getWithToken("/auth/me", login.getBody().path("token").asText());

        assertThat(registered.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(registered.getBody().has("password")).isFalse();
        assertThat(login.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(login.getBody().path("tokenType").asText()).isEqualTo("Bearer");
        assertThat(me.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(me.getBody().path("name").asText()).isEqualTo("Ana Souza");
        assertThat(me.getBody().path("email").asText()).isEqualTo(email);
    }

    @Test
    void passwordIsStoredOnlyAsBcryptHash() {
        String email = uniqueEmail();
        post("/auth/register", user("Ana", email));

        Document stored = mongo.getCollection("users").find(new Document("_id", email)).first();

        assertThat(stored).isNotNull();
        assertThat(stored.getString("passwordHash")).startsWith("$2").isNotEqualTo(PASSWORD);
        assertThat(stored.containsKey("password")).isFalse();
    }

    @Test
    void sameEmailWithDifferentCaseReturns409() {
        String email = uniqueEmail();

        ResponseEntity<JsonNode> first = post("/auth/register", user("Ana", email));
        ResponseEntity<JsonNode> second = post("/auth/register", user("Outra Ana", email.toUpperCase()));

        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(second.getBody().path("message").asText()).isEqualTo("E-mail já cadastrado");
    }

    @Test
    void concurrentRegistrationsOfTheSameEmailCreateOnlyOneUser() throws Exception {
        String email = uniqueEmail();
        int attempts = 10;
        CountDownLatch start = new CountDownLatch(1);
        List<Callable<HttpStatusCode>> tasks = IntStream.range(0, attempts)
                .<Callable<HttpStatusCode>>mapToObj(i -> () -> {
                    start.await();
                    return post("/auth/register", user("Concorrente " + i, email)).getStatusCode();
                })
                .toList();

        List<HttpStatusCode> statuses;
        try (ExecutorService pool = Executors.newFixedThreadPool(attempts)) {
            List<Future<HttpStatusCode>> futures = tasks.stream().map(pool::submit).toList();
            start.countDown();
            statuses = futures.stream().map(AuthFlowIT::get).toList();
        }

        assertThat(statuses).filteredOn(HttpStatus.CREATED::equals).hasSize(1);
        assertThat(statuses).filteredOn(HttpStatus.CONFLICT::equals).hasSize(attempts - 1);
        assertThat(mongo.getCollection("users").countDocuments(new Document("_id", email))).isEqualTo(1);
    }

    @Test
    void wrongPasswordAndUnknownEmailReturnTheSame401() {
        String email = uniqueEmail();
        post("/auth/register", user("Ana", email));

        ResponseEntity<JsonNode> wrongPassword = post("/auth/login", Map.of("email", email, "password", "senha-errada"));
        ResponseEntity<JsonNode> unknownEmail = post("/auth/login", Map.of("email", uniqueEmail(), "password", PASSWORD));

        assertThat(wrongPassword.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(unknownEmail.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(wrongPassword.getBody().path("message").asText())
                .isEqualTo(unknownEmail.getBody().path("message").asText())
                .isEqualTo("E-mail ou senha inválidos");
    }

    @Test
    void tamperedTokenIsRejected() {
        String email = uniqueEmail();
        post("/auth/register", user("Ana", email));
        String token = post("/auth/login", Map.of("email", email, "password", PASSWORD)).getBody().path("token").asText();
        String[] parts = token.split("\\.");
        String forgedPayload = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(("{\"sub\":\"admin@email.com\",\"iss\":\"auth-service\",\"exp\":4102444800}").getBytes());

        ResponseEntity<JsonNode> response = getWithToken("/auth/me", parts[0] + "." + forgedPayload + "." + parts[2]);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody().path("message").asText()).isEqualTo("Token ausente, inválido ou expirado");
    }

    private static Map<String, String> user(String name, String email) {
        return Map.of("name", name, "email", email, "password", PASSWORD);
    }

    private static String uniqueEmail() {
        return "it-" + UUID.randomUUID() + "@teste.com";
    }

    private static <T> T get(Future<T> future) {
        try {
            return future.get();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
