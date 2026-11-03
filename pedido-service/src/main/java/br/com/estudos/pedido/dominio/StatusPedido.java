package br.com.estudos.pedido.dominio;

public enum StatusPedido {
    AGUARDANDO_AGENDAMENTO,
    AGENDADO,
    SEM_AGENDA,
    CANCELADO;

    public boolean podeCancelar() {
        return this != CANCELADO;
    }
}
