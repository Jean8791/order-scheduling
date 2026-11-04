package br.com.estudos.scheduling.dto.scheduling;

import br.com.estudos.scheduling.enums.Period;
import br.com.estudos.scheduling.enums.SchedulingStatus;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Agendamento como a API devolve.
 */
public record SchedulingResponse(String orderId, SchedulingStatus status, String slotId, LocalDate date,
                                 Period period, String reason, Instant createdAt, Instant updatedAt) {
}
