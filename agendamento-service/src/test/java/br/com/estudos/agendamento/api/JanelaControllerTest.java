package br.com.estudos.agendamento.api;

import br.com.estudos.agendamento.dominio.JanelaEntrega;
import br.com.estudos.agendamento.dominio.Periodo;
import br.com.estudos.agendamento.servico.JanelaDuplicadaException;
import br.com.estudos.agendamento.servico.JanelaService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(JanelaController.class)
class JanelaControllerTest {

    private static final String JANELA = """
            {"uf":"SP","data":"2099-12-10","periodo":"MANHA","capacidade":2}
            """;

    @Autowired
    MockMvc mvc;

    @MockitoBean
    JanelaService service;

    @Test
    void criaJanelaERetorna201() throws Exception {
        when(service.criar(any())).thenReturn(JanelaEntrega.nova("SP", LocalDate.of(2099, 12, 10), Periodo.MANHA, 2, Instant.EPOCH));

        mvc.perform(post("/janelas").contentType(MediaType.APPLICATION_JSON).content(JANELA))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.uf").value("SP"))
                .andExpect(jsonPath("$.capacidade").value(2))
                .andExpect(jsonPath("$.disponiveis").value(2));
    }

    @Test
    void janelaDuplicadaRetorna409() throws Exception {
        when(service.criar(any())).thenThrow(new JanelaDuplicadaException("SP", LocalDate.of(2099, 12, 10), "MANHA"));

        mvc.perform(post("/janelas").contentType(MediaType.APPLICATION_JSON).content(JANELA))
                .andExpect(status().isConflict());
    }

    @Test
    void capacidadeZeroEPeriodoInvalidoRetornam400() throws Exception {
        mvc.perform(post("/janelas").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"uf\":\"SP\",\"data\":\"2099-12-10\",\"periodo\":\"MANHA\",\"capacidade\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.capacidade").exists());

        mvc.perform(post("/janelas").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"uf\":\"SP\",\"data\":\"2099-12-10\",\"periodo\":\"NOITE\",\"capacidade\":1}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(service);
    }

    @Test
    void listaJanelasDoPeriodo() throws Exception {
        when(service.listar("SP", LocalDate.of(2099, 12, 10), LocalDate.of(2099, 12, 17)))
                .thenReturn(List.of(JanelaEntrega.nova("SP", LocalDate.of(2099, 12, 10), Periodo.TARDE, 3, Instant.EPOCH)));

        mvc.perform(get("/janelas").param("uf", "SP").param("de", "2099-12-10").param("ate", "2099-12-17"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].periodo").value("TARDE"))
                .andExpect(jsonPath("$[0].disponiveis").value(3));
    }
}
