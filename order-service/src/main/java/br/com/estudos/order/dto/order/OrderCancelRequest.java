package br.com.estudos.order.dto.order;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record OrderCancelRequest(@NotBlank @Size(max = 255) String reason) {
}
