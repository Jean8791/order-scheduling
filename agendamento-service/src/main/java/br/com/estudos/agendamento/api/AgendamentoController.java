package br.com.estudos.agendamento.api;

import br.com.estudos.agendamento.api.dto.AgendamentoResponse;
import br.com.estudos.agendamento.servico.AgendamentoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/agendamentos")
@Tag(name = "Agendamentos", description = "Consulta do agendamento de entrega de um pedido")
public class AgendamentoController {

    private final AgendamentoService service;

    public AgendamentoController(AgendamentoService service) {
        this.service = service;
    }

    @GetMapping("/{pedidoId}")
    @Operation(summary = "Consulta o agendamento de entrega de um pedido")
    public ResponseEntity<AgendamentoResponse> buscar(@PathVariable String pedidoId) {
        return service.buscar(pedidoId)
                .map(AgendamentoResponse::de)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
