package br.com.estudos.pedido.dominio;

public class PedidoNaoEncontradoException extends RuntimeException {

    public PedidoNaoEncontradoException(String id) {
        super("Pedido " + id + " não encontrado");
    }
}
