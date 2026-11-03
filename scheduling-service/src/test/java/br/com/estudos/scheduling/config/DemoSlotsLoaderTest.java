package br.com.estudos.scheduling.config;

import br.com.estudos.scheduling.entity.DeliverySlot;
import br.com.estudos.scheduling.enums.Period;
import br.com.estudos.scheduling.repository.DeliverySlotRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DemoSlotsLoaderTest {

    private static final Instant NOW = Instant.parse("2026-11-04T02:00:00Z");
    private static final LocalDate TODAY_IN_SAO_PAULO = LocalDate.of(2026, 11, 3);

    @Mock
    DeliverySlotRepository repository;

    private DemoSlotsLoader loader() {
        return new DemoSlotsLoader(repository, new SchedulingProperties(7, ZoneId.of("America/Sao_Paulo")),
                Clock.fixed(NOW, ZoneOffset.UTC), List.of("SP", "SC", "PR"), 30, 5);
    }

    @Test
    void createsMorningAndAfternoonSlotsForEachStateAndDay() {
        loader().run(null);

        ArgumentCaptor<DeliverySlot> created = ArgumentCaptor.forClass(DeliverySlot.class);
        verify(repository, times(30 * 3 * 2)).insert(created.capture());
        assertThat(created.getAllValues()).allSatisfy(slot -> assertThat(slot.getCapacity()).isEqualTo(5));
        assertThat(created.getAllValues()).extracting(DeliverySlot::getDate)
                .contains(TODAY_IN_SAO_PAULO, TODAY_IN_SAO_PAULO.plusDays(29))
                .doesNotContain(TODAY_IN_SAO_PAULO.plusDays(30));
        assertThat(created.getAllValues()).extracting(DeliverySlot::getState).containsOnly("SP", "SC", "PR");
    }

    @Test
    void existingSlotsAreNotDuplicated() {
        when(repository.exists(anyString(), any(LocalDate.class), any(Period.class))).thenReturn(false);
        when(repository.exists(eq("SP"), eq(TODAY_IN_SAO_PAULO), eq(Period.MORNING))).thenReturn(true);

        loader().run(null);

        verify(repository, times(30 * 3 * 2 - 1)).insert(any(DeliverySlot.class));
    }

    @Test
    void secondRunCreatesNothing() {
        when(repository.exists(anyString(), any(LocalDate.class), any(Period.class))).thenReturn(true);

        loader().run(null);

        verify(repository, never()).insert(any(DeliverySlot.class));
    }
}
