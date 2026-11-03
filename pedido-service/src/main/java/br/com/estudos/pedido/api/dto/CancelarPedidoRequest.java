package br.com.estudos.pedido.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CancelarPedidoRequest(@NotBlank @Size(max = 255) String motivo) {
}
