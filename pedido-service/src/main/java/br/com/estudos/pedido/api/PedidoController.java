package br.com.estudos.pedido.api;

import br.com.estudos.pedido.api.dto.CancelarPedidoRequest;
import br.com.estudos.pedido.api.dto.CriarPedidoRequest;
import br.com.estudos.pedido.api.dto.PedidoResponse;
import br.com.estudos.pedido.dominio.Pedido;
import br.com.estudos.pedido.dominio.StatusPedido;
import br.com.estudos.pedido.servico.PedidoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;

@RestController
@RequestMapping("/pedidos")
@Tag(name = "Pedidos", description = "Criação, consulta e cancelamento de pedidos")
public class PedidoController {

    private final PedidoService service;

    public PedidoController(PedidoService service) {
        this.service = service;
    }

    @PostMapping
    @Operation(summary = "Cria um pedido e solicita o agendamento da entrega")
    public ResponseEntity<PedidoResponse> criar(@Valid @RequestBody CriarPedidoRequest request) {
        Pedido pedido = service.criar(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(pedido.getId()).toUri();
        return ResponseEntity.created(location).body(PedidoResponse.de(pedido));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Consulta um pedido")
    public PedidoResponse buscar(@PathVariable String id) {
        return PedidoResponse.de(service.buscar(id));
    }

    @GetMapping
    @Operation(summary = "Lista pedidos, opcionalmente por status")
    public Page<PedidoResponse> listar(@RequestParam(required = false) StatusPedido status,
                                       @PageableDefault(size = 20, sort = "criadoEm", direction = Sort.Direction.DESC) Pageable pageable) {
        return service.listar(status, pageable).map(PedidoResponse::de);
    }

    @PostMapping("/{id}/cancelar")
    @Operation(summary = "Cancela um pedido e libera a janela de entrega")
    public PedidoResponse cancelar(@PathVariable String id, @Valid @RequestBody CancelarPedidoRequest request) {
        return PedidoResponse.de(service.cancelar(id, request.motivo()));
    }
}
