package br.com.estudos.scheduling.service;

import br.com.estudos.scheduling.config.SchedulingProperties;
import br.com.estudos.scheduling.entity.DeliverySlot;
import br.com.estudos.scheduling.entity.Scheduling;
import br.com.estudos.scheduling.enums.Period;
import br.com.estudos.scheduling.enums.SchedulingStatus;
import br.com.estudos.scheduling.mapper.SchedulingMapper;
import br.com.estudos.scheduling.messaging.Events;
import br.com.estudos.scheduling.messaging.SchedulingEventPublisher;
import br.com.estudos.scheduling.repository.DeliverySlotRepository;
import br.com.estudos.scheduling.repository.SchedulingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.OptimisticLockingFailureException;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SchedulingServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-03T15:00:00Z");
    private static final LocalDate DESIRED = LocalDate.of(2026, 11, 10);

    @Mock
    SchedulingRepository schedulings;

    @Mock
    DeliverySlotRepository slots;

    @Mock
    SchedulingEventPublisher publisher;

    SchedulingService service;

    @BeforeEach
    void setUp() {
        service = new SchedulingService(schedulings, slots, publisher, new SchedulingMapper(),
                new SchedulingProperties(7, ZoneId.of("America/Sao_Paulo")), Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static Events.OrderCreated orderCreated(String orderId, LocalDate desired) {
        return new Events.OrderCreated("e1", NOW, orderId, "01310100", "SP", desired, 10);
    }

    private static DeliverySlot slot(LocalDate date, Period period) {
        return DeliverySlot.create("SP", date, period, 1, NOW);
    }

    private void newScheduling() {
        when(schedulings.insert(any(Scheduling.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void booksTheFirstSlotThatStillHasAvailability() {
        newScheduling();
        DeliverySlot filledDuringRace = slot(DESIRED, Period.MORNING);
        DeliverySlot free = slot(DESIRED, Period.AFTERNOON);
        when(slots.withAvailability("SP", DESIRED, DESIRED.plusDays(7))).thenReturn(List.of(filledDuringRace, free));
        when(slots.book(filledDuringRace.getId())).thenReturn(Optional.empty());
        when(slots.book(free.getId())).thenReturn(Optional.of(free));

        service.schedule(orderCreated("p1", DESIRED));

        ArgumentCaptor<Scheduling> confirmed = ArgumentCaptor.forClass(Scheduling.class);
        verify(publisher).confirmed(confirmed.capture());
        assertThat(confirmed.getValue().getStatus()).isEqualTo(SchedulingStatus.CONFIRMED);
        assertThat(confirmed.getValue().getSlotId()).isEqualTo(free.getId());
        assertThat(confirmed.getValue().getPeriod()).isEqualTo(Period.AFTERNOON);
        verify(publisher, never()).rejected(any());
        verify(slots, never()).release(anyString());
        verify(schedulings, never()).findById(anyString());
        verify(schedulings, never()).save(any());
    }

    @Test
    void cancellationDuringBookingReleasesTheSlotAndPublishesNothing() {
        DeliverySlot free = slot(DESIRED, Period.MORNING);
        when(slots.withAvailability("SP", DESIRED, DESIRED.plusDays(7))).thenReturn(List.of(free));
        when(slots.book(free.getId())).thenReturn(Optional.of(free));
        when(schedulings.insert(any(Scheduling.class))).thenThrow(new DuplicateKeyException("cancelado no meio do caminho"));
        when(schedulings.findById("p1")).thenReturn(Optional.of(Scheduling.canceledBeforeProcessing("p1", "desistiu", NOW)));

        service.schedule(orderCreated("p1", DESIRED));

        verify(slots).release(free.getId());
        verifyNoInteractions(publisher);
    }

    @Test
    void releaseFailureKeepsTheOriginalErrorForTheRetry() {
        DeliverySlot free = slot(DESIRED, Period.MORNING);
        when(slots.withAvailability("SP", DESIRED, DESIRED.plusDays(7))).thenReturn(List.of(free));
        when(slots.book(free.getId())).thenReturn(Optional.of(free));
        when(schedulings.insert(any(Scheduling.class))).thenThrow(new IllegalStateException("Mongo indisponível"));
        doThrow(new IllegalStateException("release falhou")).when(slots).release(free.getId());

        assertThatThrownBy(() -> service.schedule(orderCreated("p1", DESIRED)))
                .hasMessage("Mongo indisponível")
                .satisfies(e -> assertThat(e.getSuppressed()).extracting(Throwable::getMessage).containsExactly("release falhou"));
    }

    @Test
    void noAvailabilityWithinRangeRejectsWithReason() {
        newScheduling();
        when(slots.withAvailability("SP", DESIRED, DESIRED.plusDays(7))).thenReturn(List.of());

        service.schedule(orderCreated("p1", DESIRED));

        ArgumentCaptor<Scheduling> rejected = ArgumentCaptor.forClass(Scheduling.class);
        verify(publisher).rejected(rejected.capture());
        assertThat(rejected.getValue().getStatus()).isEqualTo(SchedulingStatus.REJECTED);
        assertThat(rejected.getValue().getReason()).contains("2026-11-17").contains("SP");
    }

    @Test
    void pastDesiredDateSearchesFromToday() {
        newScheduling();
        LocalDate todayInSaoPaulo = LocalDate.of(2026, 11, 3);
        LocalDate pastDesired = LocalDate.of(2026, 11, 1);
        when(slots.withAvailability("SP", todayInSaoPaulo, pastDesired.plusDays(7))).thenReturn(List.of());

        service.schedule(orderCreated("p1", pastDesired));

        verify(slots).withAvailability("SP", todayInSaoPaulo, LocalDate.of(2026, 11, 8));
    }

    @Test
    void tooOldDesiredDateRejectsWithoutQueryingSlots() {
        newScheduling();

        service.schedule(orderCreated("p1", LocalDate.of(2026, 10, 1)));

        verify(slots, never()).withAvailability(anyString(), any(), any());
        verify(publisher).rejected(any());
    }

    @Test
    void duplicatedEventResendsTheExistingConfirmationAndReleasesTheExtraSlot() {
        DeliverySlot extra = slot(DESIRED, Period.AFTERNOON);
        Scheduling existing = Scheduling.confirmed("p1", slot(DESIRED, Period.MORNING), NOW);
        when(slots.withAvailability("SP", DESIRED, DESIRED.plusDays(7))).thenReturn(List.of(extra));
        when(slots.book(extra.getId())).thenReturn(Optional.of(extra));
        when(schedulings.insert(any(Scheduling.class))).thenThrow(new DuplicateKeyException("duplicate _id"));
        when(schedulings.findById("p1")).thenReturn(Optional.of(existing));

        service.schedule(orderCreated("p1", DESIRED));

        verify(slots).release(extra.getId());
        verify(publisher).confirmed(existing);
        verify(publisher, never()).rejected(any());
    }

    @Test
    void concurrentConsumptionOfTheSameEventUsesTheRecordThatWonTheRace() {
        Scheduling winner = Scheduling.rejected("p1", "Sem janela", NOW);
        when(slots.withAvailability("SP", DESIRED, DESIRED.plusDays(7))).thenReturn(List.of());
        when(schedulings.insert(any(Scheduling.class))).thenThrow(new DuplicateKeyException("duplicate _id"));
        when(schedulings.findById("p1")).thenReturn(Optional.of(winner));

        service.schedule(orderCreated("p1", DESIRED));

        verify(publisher).rejected(winner);
        verify(slots, never()).release(anyString());
    }

    @Test
    void cancelingConfirmedSchedulingReleasesTheSlot() {
        DeliverySlot slot = slot(DESIRED, Period.MORNING);
        Scheduling confirmed = Scheduling.confirmed("p1", slot, NOW);
        when(schedulings.findById("p1")).thenReturn(Optional.of(confirmed));

        service.cancel(new Events.OrderCanceled("e2", NOW, "p1", "Cliente desistiu"));

        verify(slots).release(slot.getId());
        verify(schedulings).save(confirmed);
        assertThat(confirmed.getStatus()).isEqualTo(SchedulingStatus.CANCELED);
    }

    @Test
    void cancelingRejectedSchedulingDoesNotTouchSlots() {
        Scheduling rejected = Scheduling.rejected("p1", "Sem janela", NOW);
        when(schedulings.findById("p1")).thenReturn(Optional.of(rejected));

        service.cancel(new Events.OrderCanceled("e2", NOW, "p1", "Cliente desistiu"));

        verifyNoInteractions(slots);
        assertThat(rejected.getStatus()).isEqualTo(SchedulingStatus.CANCELED);
    }

    @Test
    void cancellationArrivingBeforeOrderCreatedStoresMarker() {
        when(schedulings.findById("p1")).thenReturn(Optional.empty());

        service.cancel(new Events.OrderCanceled("e2", NOW, "p1", "Cliente desistiu"));

        ArgumentCaptor<Scheduling> marker = ArgumentCaptor.forClass(Scheduling.class);
        verify(schedulings).insert(marker.capture());
        assertThat(marker.getValue().getOrderId()).isEqualTo("p1");
        assertThat(marker.getValue().getStatus()).isEqualTo(SchedulingStatus.CANCELED);
        verifyNoInteractions(slots);
    }

    @Test
    void repeatedCancellationIsIgnored() {
        when(schedulings.findById("p1")).thenReturn(Optional.of(Scheduling.canceledBeforeProcessing("p1", "desistiu", NOW)));

        service.cancel(new Events.OrderCanceled("e3", NOW, "p1", "de novo"));

        verify(schedulings, never()).save(any());
        verifyNoInteractions(slots);
    }
}
