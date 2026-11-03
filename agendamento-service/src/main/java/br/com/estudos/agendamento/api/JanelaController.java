package br.com.estudos.agendamento.api;

import br.com.estudos.agendamento.api.dto.CriarJanelaRequest;
import br.com.estudos.agendamento.api.dto.JanelaResponse;
import br.com.estudos.agendamento.dominio.JanelaEntrega;
import br.com.estudos.agendamento.servico.JanelaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
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
@RequestMapping("/janelas")
@Tag(name = "Janelas de entrega", description = "Cadastro e consulta da capacidade de entrega por UF, data e período")
public class JanelaController {

    private final JanelaService service;

    public JanelaController(JanelaService service) {
        this.service = service;
    }

    @PostMapping
    @Operation(summary = "Cadastra uma janela de entrega com capacidade")
    public ResponseEntity<JanelaResponse> criar(@Valid @RequestBody CriarJanelaRequest request) {
        JanelaEntrega janela = service.criar(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().queryParam("uf", janela.getUf())
                .queryParam("de", janela.getData()).queryParam("ate", janela.getData()).build().toUri();
        return ResponseEntity.created(location).body(JanelaResponse.de(janela));
    }

    @GetMapping
    @Operation(summary = "Lista janelas de um período, com ocupação e vagas disponíveis")
    public List<JanelaResponse> listar(@RequestParam(required = false) String uf,
                                       @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate de,
                                       @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate ate) {
        return service.listar(uf, de, ate).stream().map(JanelaResponse::de).toList();
    }
}
