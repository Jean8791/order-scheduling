package br.com.estudos.scheduling.dto.deliveryslot;

import br.com.estudos.scheduling.enums.Period;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.time.LocalDate;

public record DeliverySlotCreateRequest(
        @NotBlank @Pattern(regexp = "[A-Z]{2}") String state,
        @NotNull @FutureOrPresent LocalDate date,
        @NotNull Period period,
        @Min(1) @Max(10_000) int capacity) {
}
