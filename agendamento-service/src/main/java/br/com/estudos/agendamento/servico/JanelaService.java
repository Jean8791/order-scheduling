package br.com.estudos.agendamento.servico;

import br.com.estudos.agendamento.api.dto.CriarJanelaRequest;
import br.com.estudos.agendamento.dominio.JanelaEntrega;
import br.com.estudos.agendamento.dominio.JanelaRepository;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

@Service
public class JanelaService {

    private final JanelaRepository repository;
    private final Clock clock;

    public JanelaService(JanelaRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    public JanelaEntrega criar(CriarJanelaRequest request) {
        if (repository.existe(request.uf(), request.data(), request.periodo())) {
            throw new JanelaDuplicadaException(request.uf(), request.data(), request.periodo().name());
        }
        return repository.inserir(JanelaEntrega.nova(request.uf(), request.data(), request.periodo(), request.capacidade(), clock.instant()));
    }

    public List<JanelaEntrega> listar(String uf, LocalDate de, LocalDate ate) {
        if (ate.isBefore(de)) {
            throw new IllegalArgumentException("A data final não pode ser anterior à inicial");
        }
        return repository.listar(uf, de, ate);
    }
}
