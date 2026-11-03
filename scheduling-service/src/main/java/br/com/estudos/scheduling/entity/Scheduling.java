package br.com.estudos.scheduling.entity;

import br.com.estudos.scheduling.enums.Period;
import br.com.estudos.scheduling.enums.SchedulingStatus;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.time.LocalDate;

@Document("schedulings")
public class Scheduling {

    @Id
    private String orderId;
    private SchedulingStatus status;
    private String slotId;
    private LocalDate date;
    private Period period;
    private String reason;
    private Instant createdAt;
    private Instant updatedAt;

    @Version
    private Long version;

    protected Scheduling() {
    }

    public static Scheduling processing(String orderId, Instant now) {
        Scheduling scheduling = new Scheduling();
        scheduling.orderId = orderId;
        scheduling.status = SchedulingStatus.PROCESSING;
        scheduling.createdAt = now;
        scheduling.updatedAt = now;
        return scheduling;
    }

    public static Scheduling canceledBeforeProcessing(String orderId, String reason, Instant now) {
        Scheduling scheduling = processing(orderId, now);
        scheduling.status = SchedulingStatus.CANCELED;
        scheduling.reason = reason;
        return scheduling;
    }

    public void confirm(DeliverySlot slot, Instant now) {
        this.status = SchedulingStatus.CONFIRMED;
        this.slotId = slot.getId();
        this.date = slot.getDate();
        this.period = slot.getPeriod();
        this.reason = null;
        this.updatedAt = now;
    }

    public void reject(String reason, Instant now) {
        this.status = SchedulingStatus.REJECTED;
        this.reason = reason;
        this.updatedAt = now;
    }

    public void cancel(String reason, Instant now) {
        this.status = SchedulingStatus.CANCELED;
        this.reason = reason;
        this.updatedAt = now;
    }

    public boolean holdsSlot() {
        return status == SchedulingStatus.CONFIRMED && slotId != null;
    }

    public String getOrderId() {
        return orderId;
    }

    public SchedulingStatus getStatus() {
        return status;
    }

    public String getSlotId() {
        return slotId;
    }

    public LocalDate getDate() {
        return date;
    }

    public Period getPeriod() {
        return period;
    }

    public String getReason() {
        return reason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Long getVersion() {
        return version;
    }
}
