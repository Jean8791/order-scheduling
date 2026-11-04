package br.com.estudos.order.entity;

import java.time.LocalDate;

/**
 * Data e período em que a entrega ficou agendada.
 */
public record ScheduledDelivery(String slotId, LocalDate date, String period) {
}
