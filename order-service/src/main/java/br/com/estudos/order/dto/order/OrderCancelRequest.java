package br.com.estudos.order.dto.order;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Corpo do cancelamento: o motivo é obrigatório.
 */
public record OrderCancelRequest(@NotBlank @Size(max = 255) String reason) {
}
