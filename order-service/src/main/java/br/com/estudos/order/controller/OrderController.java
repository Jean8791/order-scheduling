package br.com.estudos.order.controller;

import br.com.estudos.order.dto.order.OrderCancelRequest;
import br.com.estudos.order.dto.order.OrderCreateRequest;
import br.com.estudos.order.dto.order.OrderResponse;
import br.com.estudos.order.enums.OrderStatus;
import br.com.estudos.order.service.OrderService;
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
@RequestMapping("/orders")
@Tag(name = "Pedidos", description = "Criação, consulta e cancelamento de pedidos")
public class OrderController {

    private final OrderService service;

    public OrderController(OrderService service) {
        this.service = service;
    }

    @PostMapping
    @Operation(summary = "Cria um pedido e solicita o agendamento da entrega")
    public ResponseEntity<OrderResponse> create(@Valid @RequestBody OrderCreateRequest request) {
        OrderResponse order = service.create(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(order.id()).toUri();
        return ResponseEntity.created(location).body(order);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Consulta um pedido")
    public OrderResponse findById(@PathVariable String id) {
        return service.findById(id);
    }

    @GetMapping
    @Operation(summary = "Lista pedidos, opcionalmente por status")
    public Page<OrderResponse> list(@RequestParam(required = false) OrderStatus status,
                                    @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return service.list(status, pageable);
    }

    @PostMapping("/{id}/cancel")
    @Operation(summary = "Cancela um pedido e libera a janela de entrega")
    public OrderResponse cancel(@PathVariable String id, @Valid @RequestBody OrderCancelRequest request) {
        return service.cancel(id, request.reason());
    }
}
