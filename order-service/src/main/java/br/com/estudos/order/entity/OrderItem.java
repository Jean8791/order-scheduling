package br.com.estudos.order.entity;

/**
 * Item do pedido, gravado dentro do documento do pedido.
 */
public record OrderItem(String sku, String description, int quantity) {
}
