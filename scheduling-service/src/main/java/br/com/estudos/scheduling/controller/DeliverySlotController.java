package br.com.estudos.scheduling.controller;

import br.com.estudos.scheduling.dto.deliveryslot.DeliverySlotCreateRequest;
import br.com.estudos.scheduling.dto.deliveryslot.DeliverySlotResponse;
import br.com.estudos.scheduling.service.DeliverySlotService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/delivery-slots")
@Tag(name = "Janelas de entrega", description = "Cadastro e consulta da capacidade de entrega por UF, data e período")
public class DeliverySlotController {

    @Autowired
    private DeliverySlotService service;

    @PostMapping
    @Operation(summary = "Cadastra uma janela de entrega com capacidade")
    public ResponseEntity<DeliverySlotResponse> create(@Valid @RequestBody DeliverySlotCreateRequest request) {
        DeliverySlotResponse slot = service.create(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().queryParam("state", slot.state())
                .queryParam("from", slot.date()).queryParam("to", slot.date()).build().toUri();
        return ResponseEntity.created(location).body(slot);
    }

    @GetMapping
    @Operation(summary = "Lista janelas de um período, com ocupação e vagas disponíveis")
    public List<DeliverySlotResponse> list(@RequestParam(required = false) String state,
                                           @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                           @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return service.list(state, from, to);
    }
}
