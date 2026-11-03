package br.com.estudos.scheduling.integration;

import br.com.estudos.scheduling.entity.DeliverySlot;
import br.com.estudos.scheduling.enums.Period;
import br.com.estudos.scheduling.messaging.Events;
import br.com.estudos.scheduling.messaging.Topology;
import br.com.estudos.scheduling.repository.DeliverySlotRepository;
import br.com.estudos.scheduling.support.IntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class SchedulingFlowIT extends IntegrationTest {

    @Autowired
    DeliverySlotRepository slots;

    @Test
    void orderCreatedBooksSlotAndCancellationReleasesIt() {
        LocalDate date = uniqueDate();
        DeliverySlot slot = slots.insert(DeliverySlot.create("SP", date, Period.MORNING, 1, Instant.now()));
        String results = listen(Topology.EXCHANGE_SCHEDULINGS, "scheduling.*");
        String orderId = UUID.randomUUID().toString();

        rabbit.convertAndSend(Topology.EXCHANGE_ORDERS, Topology.RK_ORDER_CREATED, orderCreated(orderId, date));

        Received confirmed = receiveFor(results, orderId);
        assertThat(confirmed.routingKey()).isEqualTo(Topology.RK_SCHEDULING_CONFIRMED);
        assertThat(confirmed.body().path("slotId").asText()).isEqualTo(slot.getId());
        assertThat(confirmed.body().path("period").asText()).isEqualTo("MORNING");
        assertThat(booked(slot)).isEqualTo(1);

        ResponseEntity<JsonNode> scheduling = call(HttpMethod.GET, "/schedulings/" + orderId, null);
        assertThat(scheduling.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(scheduling.getBody().path("status").asText()).isEqualTo("CONFIRMED");

        rabbit.convertAndSend(Topology.EXCHANGE_ORDERS, Topology.RK_ORDER_CANCELED,
                new Events.OrderCanceled(UUID.randomUUID().toString(), Instant.now(), orderId, "Cliente desistiu"));

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            assertThat(booked(slot)).isZero();
            assertThat(call(HttpMethod.GET, "/schedulings/" + orderId, null).getBody().path("status").asText()).isEqualTo("CANCELED");
        });
    }

    @Test
    void duplicatedOrderCreatedBooksOnlyOnceAndResendsConfirmation() {
        LocalDate date = uniqueDate();
        DeliverySlot slot = slots.insert(DeliverySlot.create("SP", date, Period.MORNING, 5, Instant.now()));
        String results = listen(Topology.EXCHANGE_SCHEDULINGS, "scheduling.*");
        String orderId = UUID.randomUUID().toString();
        Events.OrderCreated event = orderCreated(orderId, date);

        rabbit.convertAndSend(Topology.EXCHANGE_ORDERS, Topology.RK_ORDER_CREATED, event);
        Received first = receiveFor(results, orderId);
        rabbit.convertAndSend(Topology.EXCHANGE_ORDERS, Topology.RK_ORDER_CREATED, event);
        Received second = receiveFor(results, orderId);

        assertThat(first.routingKey()).isEqualTo(Topology.RK_SCHEDULING_CONFIRMED);
        assertThat(second.routingKey()).isEqualTo(Topology.RK_SCHEDULING_CONFIRMED);
        assertThat(second.body().path("slotId").asText()).isEqualTo(first.body().path("slotId").asText());
        assertThat(booked(slot)).isEqualTo(1);
    }

    @Test
    void orderWithoutAvailableSlotIsRejected() {
        String results = listen(Topology.EXCHANGE_SCHEDULINGS, "scheduling.*");
        String orderId = UUID.randomUUID().toString();

        rabbit.convertAndSend(Topology.EXCHANGE_ORDERS, Topology.RK_ORDER_CREATED, orderCreated(orderId, uniqueDate()));

        Received rejected = receiveFor(results, orderId);
        assertThat(rejected.routingKey()).isEqualTo(Topology.RK_SCHEDULING_REJECTED);
        assertThat(rejected.body().path("reason").asText()).startsWith("Sem janela de entrega disponível");
    }

    @Test
    void cancellationBeforeOrderCreatedPreventsBooking() {
        LocalDate date = uniqueDate();
        DeliverySlot slot = slots.insert(DeliverySlot.create("SP", date, Period.MORNING, 1, Instant.now()));
        String orderId = UUID.randomUUID().toString();

        rabbit.convertAndSend(Topology.EXCHANGE_ORDERS, Topology.RK_ORDER_CANCELED,
                new Events.OrderCanceled(UUID.randomUUID().toString(), Instant.now(), orderId, "Cancelado antes"));
        await().atMost(Duration.ofSeconds(10)).until(() ->
                call(HttpMethod.GET, "/schedulings/" + orderId, null).getStatusCode() == HttpStatus.OK);
        rabbit.convertAndSend(Topology.EXCHANGE_ORDERS, Topology.RK_ORDER_CREATED, orderCreated(orderId, date));

        await().pollDelay(Duration.ofSeconds(1)).atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            assertThat(call(HttpMethod.GET, "/schedulings/" + orderId, null).getBody().path("status").asText()).isEqualTo("CANCELED");
            assertThat(booked(slot)).isZero();
        });
    }

    private static Events.OrderCreated orderCreated(String orderId, LocalDate date) {
        return new Events.OrderCreated(UUID.randomUUID().toString(), Instant.now(), orderId, "01310100", "SP", date, 1);
    }

    private int booked(DeliverySlot slot) {
        return slots.list(slot.getState(), slot.getDate(), slot.getDate()).stream()
                .filter(found -> found.getId().equals(slot.getId()))
                .findFirst()
                .orElseThrow()
                .getBooked();
    }
}
