package br.com.estudos.order.integration;

import br.com.estudos.order.messaging.Events;
import br.com.estudos.order.messaging.Topology;
import br.com.estudos.order.support.IntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class OrderFlowIT extends IntegrationTest {

    @Test
    void createdOrderPublishesEventAndConfirmationSchedulesIt() {
        String events = listen(Topology.EXCHANGE_ORDERS, "order.*");
        LocalDate date = uniqueDate();

        ResponseEntity<JsonNode> created = call(HttpMethod.POST, "/orders", order(date));

        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String orderId = created.getBody().path("id").asText();
        assertThat(created.getBody().path("status").asText()).isEqualTo("AWAITING_SCHEDULING");
        assertThat(created.getHeaders().getLocation()).hasPath("/orders/" + orderId);

        Received event = receiveFor(events, orderId);
        assertThat(event.routingKey()).isEqualTo(Topology.RK_ORDER_CREATED);
        assertThat(event.body().path("state").asText()).isEqualTo("SP");
        assertThat(event.body().path("desiredDeliveryDate").asText()).isEqualTo(date.toString());
        assertThat(event.body().path("itemCount").asInt()).isEqualTo(3);

        rabbit.convertAndSend(Topology.EXCHANGE_SCHEDULINGS, Topology.RK_SCHEDULING_CONFIRMED,
                new Events.SchedulingConfirmed(UUID.randomUUID().toString(), Instant.now(), orderId, "slot-1", date, "MORNING"));

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            JsonNode order = call(HttpMethod.GET, "/orders/" + orderId, null).getBody();
            assertThat(order.path("status").asText()).isEqualTo("SCHEDULED");
            assertThat(order.path("scheduledDelivery").path("slotId").asText()).isEqualTo("slot-1");
            assertThat(order.path("scheduledDelivery").path("period").asText()).isEqualTo("MORNING");
        });
    }

    @Test
    void rejectionMarksOrderAsNotScheduledAndLateConfirmationIsIgnored() {
        LocalDate date = uniqueDate();
        String orderId = call(HttpMethod.POST, "/orders", order(date)).getBody().path("id").asText();

        rabbit.convertAndSend(Topology.EXCHANGE_SCHEDULINGS, Topology.RK_SCHEDULING_REJECTED,
                new Events.SchedulingRejected(UUID.randomUUID().toString(), Instant.now(), orderId, "Sem janela"));
        await().atMost(Duration.ofSeconds(10)).until(() ->
                "NOT_SCHEDULED".equals(call(HttpMethod.GET, "/orders/" + orderId, null).getBody().path("status").asText()));

        rabbit.convertAndSend(Topology.EXCHANGE_SCHEDULINGS, Topology.RK_SCHEDULING_CONFIRMED,
                new Events.SchedulingConfirmed(UUID.randomUUID().toString(), Instant.now(), orderId, "slot-1", date, "MORNING"));

        await().pollDelay(Duration.ofSeconds(1)).atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            JsonNode order = call(HttpMethod.GET, "/orders/" + orderId, null).getBody();
            assertThat(order.path("status").asText()).isEqualTo("NOT_SCHEDULED");
            assertThat(order.path("reason").asText()).isEqualTo("Sem janela");
        });
    }

    @Test
    void cancellationPublishesEventAndSecondCancellationReturns422() {
        String events = listen(Topology.EXCHANGE_ORDERS, "order.canceled");
        String orderId = call(HttpMethod.POST, "/orders", order(uniqueDate())).getBody().path("id").asText();

        ResponseEntity<JsonNode> canceled = call(HttpMethod.POST, "/orders/" + orderId + "/cancel", Map.of("reason", "Cliente desistiu"));
        ResponseEntity<JsonNode> again = call(HttpMethod.POST, "/orders/" + orderId + "/cancel", Map.of("reason", "De novo"));

        assertThat(canceled.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(canceled.getBody().path("status").asText()).isEqualTo("CANCELED");
        Received event = receiveFor(events, orderId);
        assertThat(event.routingKey()).isEqualTo(Topology.RK_ORDER_CANCELED);
        assertThat(event.body().path("reason").asText()).isEqualTo("Cliente desistiu");
        assertThat(again.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Test
    void invalidOrderReturns400AndMissingOrderReturns404() {
        ResponseEntity<JsonNode> invalid = call(HttpMethod.POST, "/orders", Map.of("customer", "", "items", List.of()));
        ResponseEntity<JsonNode> missing = call(HttpMethod.GET, "/orders/" + UUID.randomUUID(), null);

        assertThat(invalid.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(invalid.getBody().path("fieldErrors")).isNotEmpty();
        assertThat(missing.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void requestWithoutTokenReturns401() {
        ResponseEntity<JsonNode> response = rest.getForEntity("/orders", JsonNode.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody().path("code").asText()).isEqualTo("UNAUTHORIZED");
    }

    private static Map<String, Object> order(LocalDate date) {
        return Map.of(
                "customer", "Loja Integração",
                "address", Map.of("street", "Av. Paulista", "number", "1000", "city", "São Paulo", "state", "SP", "zipCode", "01310100"),
                "items", List.of(Map.of("sku", "CAFE-500G", "description", "Café 500g", "quantity", 3)),
                "desiredDeliveryDate", date.toString());
    }
}
