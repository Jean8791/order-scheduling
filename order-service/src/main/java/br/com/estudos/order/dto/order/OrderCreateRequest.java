package br.com.estudos.order.dto.order;

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

/**
 * Corpo do {@code POST /orders}. {@code @Valid} nos campos aninhados valida
 * também o endereço e cada item.
 */
public record OrderCreateRequest(
        @NotBlank @Size(max = 120) String customer,
        @NotNull @Valid AddressRequest address,
        @NotEmpty @Size(max = 100) List<@Valid ItemRequest> items,
        @NotNull @FutureOrPresent LocalDate desiredDeliveryDate) {

    public record AddressRequest(
            @NotBlank String street,
            @NotBlank String number,
            @NotBlank String city,
            @NotBlank @Pattern(regexp = "[A-Z]{2}") String state,
            @NotBlank @Pattern(regexp = "\\d{8}") String zipCode) {
    }

    public record ItemRequest(
            @NotBlank String sku,
            @NotBlank String description,
            @Min(1) int quantity) {
    }
}
