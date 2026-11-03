package br.com.estudos.pedido.mensageria;

public final class Topologia {

    public static final String EXCHANGE_PEDIDOS = "pedidos.events";
    public static final String EXCHANGE_AGENDAMENTOS = "agendamentos.events";
    public static final String EXCHANGE_DLX = "pedido-service.dlx";

    public static final String RK_PEDIDO_CRIADO = "pedido.criado";
    public static final String RK_PEDIDO_CANCELADO = "pedido.cancelado";
    public static final String RK_AGENDAMENTO_CONFIRMADO = "agendamento.confirmado";
    public static final String RK_AGENDAMENTO_RECUSADO = "agendamento.recusado";

    public static final String FILA_AGENDAMENTO_CONFIRMADO = "pedido-service.agendamento-confirmado";
    public static final String FILA_AGENDAMENTO_RECUSADO = "pedido-service.agendamento-recusado";

    private Topologia() {
    }
}
