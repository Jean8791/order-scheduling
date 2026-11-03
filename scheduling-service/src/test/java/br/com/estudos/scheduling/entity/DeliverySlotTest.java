package br.com.estudos.scheduling.entity;

import br.com.estudos.scheduling.enums.Period;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DeliverySlotTest {

    @Test
    void newSlotStartsEmpty() {
        DeliverySlot slot = DeliverySlot.create("SP", LocalDate.of(2026, 12, 10), Period.MORNING, 5, Instant.EPOCH);

        assertThat(slot.getId()).isNotBlank();
        assertThat(slot.getBooked()).isZero();
        assertThat(slot.available()).isEqualTo(5);
    }

    @Test
    void capacityMustBePositive() {
        assertThatThrownBy(() -> DeliverySlot.create("SP", LocalDate.of(2026, 12, 10), Period.MORNING, 0, Instant.EPOCH))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
