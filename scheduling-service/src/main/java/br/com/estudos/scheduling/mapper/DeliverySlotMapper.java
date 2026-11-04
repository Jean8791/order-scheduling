package br.com.estudos.scheduling.mapper;

import br.com.estudos.scheduling.dto.deliveryslot.DeliverySlotCreateRequest;
import br.com.estudos.scheduling.dto.deliveryslot.DeliverySlotResponse;
import br.com.estudos.scheduling.entity.DeliverySlot;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Converte entre DTOs e a entidade {@code DeliverySlot}.
 */
@Component
public class DeliverySlotMapper {

    public DeliverySlot toEntity(DeliverySlotCreateRequest req, Instant now) {
        return DeliverySlot.create(req.state(), req.date(), req.period(), req.capacity(), now);
    }

    public DeliverySlotResponse toResponse(DeliverySlot slot) {
        return new DeliverySlotResponse(slot.getId(), slot.getState(), slot.getDate(), slot.getPeriod(),
                slot.getCapacity(), slot.getBooked(), slot.available());
    }
}
