package br.com.estudos.agendamento.mensageria;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarable;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

import static br.com.estudos.agendamento.mensageria.Topologia.*;

@Configuration
public class MensageriaConfig {

    @Bean
    public MessageConverter messageConverter(ObjectMapper objectMapper) {
        Jackson2JsonMessageConverter converter = new Jackson2JsonMessageConverter(objectMapper);
        converter.setAlwaysConvertToInferredType(true);
        return converter;
    }

    @Bean
    public Declarables topologia() {
        TopicExchange pedidos = new TopicExchange(EXCHANGE_PEDIDOS, true, false);
        TopicExchange agendamentos = new TopicExchange(EXCHANGE_AGENDAMENTOS, true, false);
        DirectExchange dlx = new DirectExchange(EXCHANGE_DLX, true, false);

        List<Declarable> declaraveis = new ArrayList<>(List.of(pedidos, agendamentos, dlx));
        declaraveis.addAll(filaComDlq(FILA_PEDIDO_CRIADO, pedidos, RK_PEDIDO_CRIADO, dlx));
        declaraveis.addAll(filaComDlq(FILA_PEDIDO_CANCELADO, pedidos, RK_PEDIDO_CANCELADO, dlx));
        return new Declarables(declaraveis);
    }

    static List<Declarable> filaComDlq(String nome, TopicExchange origem, String routingKey, DirectExchange dlx) {
        Queue fila = QueueBuilder.durable(nome)
                .deadLetterExchange(dlx.getName())
                .deadLetterRoutingKey(nome)
                .build();
        Queue dlq = QueueBuilder.durable(nome + ".dlq").build();
        Binding ligacao = BindingBuilder.bind(fila).to(origem).with(routingKey);
        Binding ligacaoDlq = BindingBuilder.bind(dlq).to(dlx).with(nome);
        return List.of(fila, dlq, ligacao, ligacaoDlq);
    }
}
