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
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;

/**
 * Endpoints de pedidos (Spring MVC).
 *
 * <p>O parâmetro {@link org.springframework.security.core.Authentication} é
 * preenchido pelo Spring Security com o usuário do token; {@code getName()}
 * devolve o {@code sub} do JWT (o e-mail). É com ele que cada pedido fica
 * ligado ao seu dono.
 */
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
    public ResponseEntity<OrderResponse> create(@Valid @RequestBody OrderCreateRequest request, Authentication user) {
        OrderResponse order = service.create(request, user.getName());
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(order.id()).toUri();
        return ResponseEntity.created(location).body(order);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Consulta um pedido")
    public OrderResponse findById(@PathVariable String id, Authentication user) {
        return service.findById(id, user.getName());
    }

    @GetMapping
    @Operation(summary = "Lista os pedidos do usuário logado, opcionalmente por status")
    public Page<OrderResponse> list(@RequestParam(required = false) OrderStatus status,
                                    @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
                                    Authentication user) {
        return service.list(status, user.getName(), pageable);
    }

    @PostMapping("/{id}/cancel")
    @Operation(summary = "Cancela um pedido e libera a janela de entrega")
    public OrderResponse cancel(@PathVariable String id, @Valid @RequestBody OrderCancelRequest request, Authentication user) {
        return service.cancel(id, request.reason(), user.getName());
    }
}
