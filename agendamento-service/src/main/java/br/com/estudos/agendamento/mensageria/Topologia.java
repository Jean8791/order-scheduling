package br.com.estudos.agendamento.mensageria;

public final class Topologia {

    public static final String EXCHANGE_PEDIDOS = "pedidos.events";
    public static final String EXCHANGE_AGENDAMENTOS = "agendamentos.events";
    public static final String EXCHANGE_DLX = "agendamento-service.dlx";

    public static final String RK_PEDIDO_CRIADO = "pedido.criado";
    public static final String RK_PEDIDO_CANCELADO = "pedido.cancelado";
    public static final String RK_AGENDAMENTO_CONFIRMADO = "agendamento.confirmado";
    public static final String RK_AGENDAMENTO_RECUSADO = "agendamento.recusado";

    public static final String FILA_PEDIDO_CRIADO = "agendamento-service.pedido-criado";
    public static final String FILA_PEDIDO_CANCELADO = "agendamento-service.pedido-cancelado";

    private Topologia() {
    }
}
