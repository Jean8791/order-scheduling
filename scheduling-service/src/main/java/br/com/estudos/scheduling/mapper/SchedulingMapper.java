package br.com.estudos.scheduling.mapper;

import br.com.estudos.scheduling.dto.scheduling.SchedulingResponse;
import br.com.estudos.scheduling.entity.Scheduling;
import org.springframework.stereotype.Component;

@Component
public class SchedulingMapper {

    public SchedulingResponse toResponse(Scheduling scheduling) {
        return new SchedulingResponse(scheduling.getOrderId(), scheduling.getStatus(), scheduling.getSlotId(),
                scheduling.getDate(), scheduling.getPeriod(), scheduling.getReason(),
                scheduling.getCreatedAt(), scheduling.getUpdatedAt());
    }
}
