package br.com.estudos.scheduling.integration;

import br.com.estudos.scheduling.entity.DeliverySlot;
import br.com.estudos.scheduling.enums.Period;
import br.com.estudos.scheduling.repository.DeliverySlotRepository;
import br.com.estudos.scheduling.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class DeliverySlotRepositoryIT extends IntegrationTest {

    @Autowired
    DeliverySlotRepository repository;

    @Test
    void concurrentBookingsNeverExceedCapacity() throws Exception {
        DeliverySlot slot = repository.insert(DeliverySlot.create("SP", uniqueDate(), Period.MORNING, 3, Instant.now()));
        int attempts = 20;
        CountDownLatch start = new CountDownLatch(1);
        List<Callable<Optional<DeliverySlot>>> tasks = IntStream.range(0, attempts)
                .<Callable<Optional<DeliverySlot>>>mapToObj(i -> () -> {
                    start.await();
                    return repository.book(slot.getId());
                })
                .toList();

        long booked;
        try (ExecutorService pool = Executors.newFixedThreadPool(attempts)) {
            List<Future<Optional<DeliverySlot>>> futures = tasks.stream().map(pool::submit).toList();
            start.countDown();
            booked = 0;
            for (Future<Optional<DeliverySlot>> future : futures) {
                if (future.get().isPresent()) {
                    booked++;
                }
            }
        }

        assertThat(booked).isEqualTo(3);
        assertThat(reload(slot).getBooked()).isEqualTo(3);
    }

    @Test
    void releaseGivesTheSpotBackButNeverGoesBelowZero() {
        DeliverySlot slot = repository.insert(DeliverySlot.create("SP", uniqueDate(), Period.MORNING, 1, Instant.now()));

        assertThat(repository.release(slot.getId())).isFalse();
        assertThat(repository.book(slot.getId())).isPresent();
        assertThat(repository.book(slot.getId())).isEmpty();
        assertThat(repository.release(slot.getId())).isTrue();
        assertThat(repository.release(slot.getId())).isFalse();
        assertThat(reload(slot).getBooked()).isZero();
    }

    @Test
    void withAvailabilitySkipsFullSlotsAndOrdersByDateThenPeriod() {
        LocalDate day1 = uniqueDate();
        LocalDate day2 = day1.plusDays(1);
        DeliverySlot day2Morning = repository.insert(DeliverySlot.create("SP", day2, Period.MORNING, 1, Instant.now()));
        DeliverySlot day1Afternoon = repository.insert(DeliverySlot.create("SP", day1, Period.AFTERNOON, 1, Instant.now()));
        DeliverySlot day1MorningFull = repository.insert(DeliverySlot.create("SP", day1, Period.MORNING, 1, Instant.now()));
        repository.insert(DeliverySlot.create("RJ", day1, Period.MORNING, 1, Instant.now()));
        repository.book(day1MorningFull.getId());

        List<DeliverySlot> available = repository.withAvailability("SP", day1, day2);

        assertThat(available).extracting(DeliverySlot::getId).containsExactly(day1Afternoon.getId(), day2Morning.getId());
    }

    private DeliverySlot reload(DeliverySlot slot) {
        return repository.list(slot.getState(), slot.getDate(), slot.getDate()).stream()
                .filter(found -> found.getId().equals(slot.getId()))
                .findFirst()
                .orElseThrow();
    }
}
