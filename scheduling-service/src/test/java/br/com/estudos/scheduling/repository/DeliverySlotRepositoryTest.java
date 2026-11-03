package br.com.estudos.scheduling.repository;

import br.com.estudos.scheduling.entity.DeliverySlot;
import br.com.estudos.scheduling.enums.Period;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeliverySlotRepositoryTest {

    private static final LocalDate DAY_1 = LocalDate.of(2026, 12, 10);
    private static final LocalDate DAY_2 = LocalDate.of(2026, 12, 11);

    @Mock
    MongoTemplate mongo;

    @Test
    void slotsWithAvailabilityComeOrderedByDateThenMorningBeforeAfternoon() {
        DeliverySlot day2Morning = DeliverySlot.create("SP", DAY_2, Period.MORNING, 1, Instant.EPOCH);
        DeliverySlot day1Afternoon = DeliverySlot.create("SP", DAY_1, Period.AFTERNOON, 1, Instant.EPOCH);
        DeliverySlot day1Morning = DeliverySlot.create("SP", DAY_1, Period.MORNING, 1, Instant.EPOCH);
        when(mongo.find(any(Query.class), eq(DeliverySlot.class))).thenReturn(List.of(day2Morning, day1Afternoon, day1Morning));

        List<DeliverySlot> slots = new DeliverySlotRepository(mongo).withAvailability("SP", DAY_1, DAY_2);

        assertThat(slots).containsExactly(day1Morning, day1Afternoon, day2Morning);
    }
}
