package br.com.estudos.scheduling.entity;

import br.com.estudos.scheduling.enums.Period;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Document("deliverySlots")
public class DeliverySlot {

    @Id
    private String id;
    private String state;
    private LocalDate date;
    private Period period;
    private int capacity;
    private int booked;
    private Instant createdAt;

    protected DeliverySlot() {
    }

    public static DeliverySlot create(String state, LocalDate date, Period period, int capacity, Instant now) {
        if (capacity < 1) {
            throw new IllegalArgumentException("Delivery slot capacity must be greater than zero");
        }
        DeliverySlot slot = new DeliverySlot();
        slot.id = UUID.randomUUID().toString();
        slot.state = state;
        slot.date = date;
        slot.period = period;
        slot.capacity = capacity;
        slot.booked = 0;
        slot.createdAt = now;
        return slot;
    }

    public int available() {
        return Math.max(0, capacity - booked);
    }

    public String getId() {
        return id;
    }

    public String getState() {
        return state;
    }

    public LocalDate getDate() {
        return date;
    }

    public Period getPeriod() {
        return period;
    }

    public int getCapacity() {
        return capacity;
    }

    public int getBooked() {
        return booked;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
