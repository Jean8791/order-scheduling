package br.com.estudos.agendamento.api.dto;

import br.com.estudos.agendamento.dominio.Periodo;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.time.LocalDate;

public record CriarJanelaRequest(
        @NotBlank @Pattern(regexp = "[A-Z]{2}") String uf,
        @NotNull @FutureOrPresent LocalDate data,
        @NotNull Periodo periodo,
        @Min(1) @Max(10_000) int capacidade) {
}
