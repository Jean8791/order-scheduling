package br.com.estudos.scheduling.controller;

import br.com.estudos.scheduling.dto.scheduling.SchedulingResponse;
import br.com.estudos.scheduling.service.SchedulingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Consulta operacional do agendamento de um pedido (somente ADMIN).
 *
 * <p>O cliente acompanha o próprio pedido pelo order-service; aqui é a visão
 * de quem opera as entregas.
 */
@RestController
@RequestMapping("/schedulings")
@Tag(name = "Agendamentos", description = "Consulta do agendamento de entrega de um pedido")
public class SchedulingController {

    private final SchedulingService service;

    public SchedulingController(SchedulingService service) {
        this.service = service;
    }

    @GetMapping("/{orderId}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Consulta o agendamento de entrega de um pedido (somente ADMIN)")
    public ResponseEntity<SchedulingResponse> findByOrderId(@PathVariable String orderId) {
        return service.findByOrderId(orderId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
