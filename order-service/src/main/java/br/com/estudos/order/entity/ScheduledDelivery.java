package br.com.estudos.order.entity;

import java.time.LocalDate;

public record ScheduledDelivery(String slotId, LocalDate date, String period) {
}
