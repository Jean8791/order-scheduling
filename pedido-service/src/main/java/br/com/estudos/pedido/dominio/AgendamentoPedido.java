package br.com.estudos.pedido.dominio;

import java.time.LocalDate;

public record AgendamentoPedido(String janelaId, LocalDate data, String periodo) {
}
