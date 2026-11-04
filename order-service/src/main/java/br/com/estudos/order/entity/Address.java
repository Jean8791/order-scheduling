package br.com.estudos.order.entity;

/**
 * Endereço de entrega, gravado dentro do documento do pedido.
 */
public record Address(String street, String number, String city, String state, String zipCode) {
}
