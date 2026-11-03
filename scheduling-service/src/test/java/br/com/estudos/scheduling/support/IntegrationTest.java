package br.com.estudos.scheduling.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.fail;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(ContainersConfig.class)
public abstract class IntegrationTest {

    private static final TestKeys KEYS = TestKeys.generate();
    private static final AtomicInteger DATES = new AtomicInteger();
    private static final long RECEIVE_TIMEOUT_MS = 10_000;

    @Autowired
    protected TestRestTemplate rest;

    @Autowired
    protected RabbitTemplate rabbit;

    @Autowired
    protected AmqpAdmin amqpAdmin;

    @Autowired
    protected ObjectMapper objectMapper;

    @DynamicPropertySource
    static void jwt(DynamicPropertyRegistry registry) {
        registry.add("jwt.public-key", KEYS::publicKeyLocation);
    }

    protected static LocalDate uniqueDate() {
        return LocalDate.of(2100, 1, 1).plusDays(30L * DATES.incrementAndGet());
    }

    protected HttpHeaders authHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(KEYS.token("teste@email.com", "auth-service"));
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    protected String tokenFromAnotherIssuer() {
        return KEYS.token("teste@email.com", "outro-emissor");
    }

    protected ResponseEntity<JsonNode> call(HttpMethod method, String path, Object body) {
        return rest.exchange(path, method, new HttpEntity<>(body, authHeaders()), JsonNode.class);
    }

    protected String listen(String exchange, String routingKey) {
        Queue queue = new Queue("it." + UUID.randomUUID(), false, false, false);
        amqpAdmin.declareQueue(queue);
        TopicExchange topic = new TopicExchange(exchange, true, false);
        amqpAdmin.declareExchange(topic);
        amqpAdmin.declareBinding(BindingBuilder.bind(queue).to(topic).with(routingKey));
        return queue.getName();
    }

    protected Received receiveFor(String queue, String orderId) {
        long deadline = System.currentTimeMillis() + RECEIVE_TIMEOUT_MS;
        while (System.currentTimeMillis() < deadline) {
            Message message = rabbit.receive(queue, 500);
            if (message == null) {
                continue;
            }
            JsonNode body = readTree(message.getBody());
            if (orderId.equals(body.path("orderId").asText())) {
                return new Received(message.getMessageProperties().getReceivedRoutingKey(), body);
            }
        }
        return fail("Nenhuma mensagem do pedido " + orderId + " chegou na fila " + queue);
    }

    private JsonNode readTree(byte[] body) {
        try {
            return objectMapper.readTree(body);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public record Received(String routingKey, JsonNode body) {
    }
}
