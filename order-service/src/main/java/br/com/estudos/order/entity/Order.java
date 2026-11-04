package br.com.estudos.order.entity;

import br.com.estudos.order.enums.OrderStatus;
import br.com.estudos.order.exception.BusinessException;
import br.com.estudos.order.exception.ErrorCode;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Document("orders")
public class Order {

    @Id
    private String id;
    private String owner;
    private String customer;
    private Address address;
    private List<OrderItem> items;
    private LocalDate desiredDeliveryDate;
    private OrderStatus status;
    private ScheduledDelivery scheduledDelivery;
    private String reason;
    private Instant createdAt;
    private Instant updatedAt;

    @Version
    private Long version;

    protected Order() {
    }

    public static Order create(String owner, String customer, Address address, List<OrderItem> items, LocalDate desiredDeliveryDate, Instant now) {
        Order order = new Order();
        order.id = UUID.randomUUID().toString();
        order.owner = owner;
        order.customer = customer;
        order.address = address;
        order.items = List.copyOf(items);
        order.desiredDeliveryDate = desiredDeliveryDate;
        order.status = OrderStatus.AWAITING_SCHEDULING;
        order.createdAt = now;
        order.updatedAt = now;
        return order;
    }

    public boolean confirmScheduling(ScheduledDelivery scheduledDelivery, Instant now) {
        if (status != OrderStatus.AWAITING_SCHEDULING) {
            return false;
        }
        this.scheduledDelivery = scheduledDelivery;
        this.status = OrderStatus.SCHEDULED;
        this.reason = null;
        this.updatedAt = now;
        return true;
    }

    public boolean rejectScheduling(String reason, Instant now) {
        if (status != OrderStatus.AWAITING_SCHEDULING) {
            return false;
        }
        this.status = OrderStatus.NOT_SCHEDULED;
        this.reason = reason;
        this.updatedAt = now;
        return true;
    }

    public void cancel(String reason, Instant now) {
        if (!status.canBeCanceled()) {
            throw BusinessException.of(ErrorCode.UNPROCESSABLE, "Pedido " + id + " já está cancelado");
        }
        this.status = OrderStatus.CANCELED;
        this.reason = reason;
        this.updatedAt = now;
    }

    public String getId() {
        return id;
    }

    public String getOwner() {
        return owner;
    }

    public String getCustomer() {
        return customer;
    }

    public Address getAddress() {
        return address;
    }

    public List<OrderItem> getItems() {
        return items;
    }

    public LocalDate getDesiredDeliveryDate() {
        return desiredDeliveryDate;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public ScheduledDelivery getScheduledDelivery() {
        return scheduledDelivery;
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
