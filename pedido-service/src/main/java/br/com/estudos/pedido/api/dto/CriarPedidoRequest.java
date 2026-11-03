package br.com.estudos.pedido.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

public record CriarPedidoRequest(
        @NotBlank @Size(max = 120) String cliente,
        @NotNull @Valid EnderecoRequest endereco,
        @NotEmpty @Size(max = 100) List<@Valid ItemRequest> itens,
        @NotNull @FutureOrPresent LocalDate dataEntregaDesejada) {

    public record EnderecoRequest(
            @NotBlank String logradouro,
            @NotBlank String numero,
            @NotBlank String cidade,
            @NotBlank @Pattern(regexp = "[A-Z]{2}") String uf,
            @NotBlank @Pattern(regexp = "\\d{8}") String cep) {
    }

    public record ItemRequest(
            @NotBlank String sku,
            @NotBlank String descricao,
            @Min(1) int quantidade) {
    }
}
