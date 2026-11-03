package br.com.estudos.pedido.servico;

import br.com.estudos.pedido.api.dto.CriarPedidoRequest;
import br.com.estudos.pedido.dominio.AgendamentoPedido;
import br.com.estudos.pedido.dominio.Endereco;
import br.com.estudos.pedido.dominio.ItemPedido;
import br.com.estudos.pedido.dominio.Pedido;
import br.com.estudos.pedido.dominio.PedidoNaoEncontradoException;
import br.com.estudos.pedido.dominio.PedidoRepository;
import br.com.estudos.pedido.dominio.StatusPedido;
import br.com.estudos.pedido.mensageria.Eventos;
import br.com.estudos.pedido.mensageria.PedidoEventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.List;

@Service
public class PedidoService {

    private static final Logger log = LoggerFactory.getLogger(PedidoService.class);

    private final PedidoRepository repository;
    private final PedidoEventPublisher publisher;
    private final Clock clock;

    public PedidoService(PedidoRepository repository, PedidoEventPublisher publisher, Clock clock) {
        this.repository = repository;
        this.publisher = publisher;
        this.clock = clock;
    }

    public Pedido criar(CriarPedidoRequest request) {
        Endereco endereco = new Endereco(request.endereco().logradouro(), request.endereco().numero(),
                request.endereco().cidade(), request.endereco().uf(), request.endereco().cep());
        List<ItemPedido> itens = request.itens().stream()
                .map(item -> new ItemPedido(item.sku(), item.descricao(), item.quantidade()))
                .toList();

        Pedido pedido = repository.save(Pedido.novo(request.cliente(), endereco, itens, request.dataEntregaDesejada(), clock.instant()));
        publisher.pedidoCriado(pedido);
        log.info("Pedido {} criado, aguardando agendamento para {}", pedido.getId(), pedido.getDataEntregaDesejada());
        return pedido;
    }

    public Pedido buscar(String id) {
        return repository.findById(id).orElseThrow(() -> new PedidoNaoEncontradoException(id));
    }

    public Page<Pedido> listar(StatusPedido status, Pageable pageable) {
        return status == null ? repository.findAll(pageable) : repository.findByStatus(status, pageable);
    }

    public Pedido cancelar(String id, String motivo) {
        Pedido pedido = buscar(id);
        pedido.cancelar(motivo, clock.instant());
        Pedido salvo = repository.save(pedido);
        publisher.pedidoCancelado(salvo);
        log.info("Pedido {} cancelado", id);
        return salvo;
    }

    public void registrarAgendamentoConfirmado(Eventos.AgendamentoConfirmado evento) {
        Pedido pedido = buscar(evento.pedidoId());
        AgendamentoPedido agendamento = new AgendamentoPedido(evento.janelaId(), evento.data(), evento.periodo());
        if (pedido.confirmarAgendamento(agendamento, clock.instant())) {
            repository.save(pedido);
            log.info("Pedido {} agendado para {} ({})", pedido.getId(), evento.data(), evento.periodo());
        } else {
            log.info("Confirmação de agendamento ignorada: pedido {} está {}", pedido.getId(), pedido.getStatus());
        }
    }

    public void registrarAgendamentoRecusado(Eventos.AgendamentoRecusado evento) {
        Pedido pedido = buscar(evento.pedidoId());
        if (pedido.recusarAgendamento(evento.motivo(), clock.instant())) {
            repository.save(pedido);
            log.info("Pedido {} sem agenda: {}", pedido.getId(), evento.motivo());
        } else {
            log.info("Recusa de agendamento ignorada: pedido {} está {}", pedido.getId(), pedido.getStatus());
        }
    }
}
