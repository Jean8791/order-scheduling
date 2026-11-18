package br.com.estudos.scheduling.entity;

import br.com.estudos.scheduling.enums.Period;
import br.com.estudos.scheduling.enums.SchedulingStatus;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Agendamento de um pedido, na coleção {@code schedulings}.
 *
 * <p>O {@code _id} é o próprio id do pedido, o que garante um agendamento por
 * pedido. {@code @Version} protege contra atualizações concorrentes.
 */
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

    public static Scheduling confirmed(String orderId, DeliverySlot slot, Instant now) {
        Scheduling scheduling = create(orderId, SchedulingStatus.CONFIRMED, null, now);
        scheduling.slotId = slot.getId();
        scheduling.date = slot.getDate();
        scheduling.period = slot.getPeriod();
        return scheduling;
    }

    public static Scheduling rejected(String orderId, String reason, Instant now) {
        return create(orderId, SchedulingStatus.REJECTED, reason, now);
    }

    public static Scheduling canceledBeforeProcessing(String orderId, String reason, Instant now) {
        return create(orderId, SchedulingStatus.CANCELED, reason, now);
    }

    private static Scheduling create(String orderId, SchedulingStatus status, String reason, Instant now) {
        Scheduling scheduling = new Scheduling();
        scheduling.orderId = orderId;
        scheduling.status = status;
        scheduling.reason = reason;
        scheduling.createdAt = now;
        scheduling.updatedAt = now;
        return scheduling;
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
