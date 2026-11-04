package br.com.estudos.scheduling.service;

import br.com.estudos.scheduling.dto.deliveryslot.DeliverySlotCreateRequest;
import br.com.estudos.scheduling.dto.deliveryslot.DeliverySlotResponse;
import br.com.estudos.scheduling.exception.BusinessException;
import br.com.estudos.scheduling.exception.ErrorCode;
import br.com.estudos.scheduling.mapper.DeliverySlotMapper;
import br.com.estudos.scheduling.repository.DeliverySlotRepository;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

/**
 * Regras das janelas de entrega: cadastro sem duplicidade e consulta por
 * período.
 */
@Service
public class DeliverySlotService {

    private final DeliverySlotRepository repository;
    private final DeliverySlotMapper mapper;
    private final Clock clock;

    public DeliverySlotService(DeliverySlotRepository repository, DeliverySlotMapper mapper, Clock clock) {
        this.repository = repository;
        this.mapper = mapper;
        this.clock = clock;
    }

    public DeliverySlotResponse create(DeliverySlotCreateRequest request) {
        if (repository.exists(request.state(), request.date(), request.period())) {
            throw BusinessException.of(ErrorCode.CONFLICT,
                    "Já existe janela para " + request.state() + " em " + request.date() + " no período " + request.period());
        }
        return mapper.toResponse(repository.insert(mapper.toEntity(request, clock.instant())));
    }

    public List<DeliverySlotResponse> list(String state, LocalDate from, LocalDate to) {
        if (to.isBefore(from)) {
            throw BusinessException.of(ErrorCode.BAD_REQUEST, "A data final não pode ser anterior à inicial");
        }
        return repository.list(state, from, to).stream().map(mapper::toResponse).toList();
    }
}
