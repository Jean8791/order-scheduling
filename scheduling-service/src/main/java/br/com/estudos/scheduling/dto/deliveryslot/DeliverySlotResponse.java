package br.com.estudos.scheduling.dto.deliveryslot;

import br.com.estudos.scheduling.enums.Period;

import java.time.LocalDate;

public record DeliverySlotResponse(String id, String state, LocalDate date, Period period, int capacity, int booked, int available) {
}
