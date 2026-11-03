package br.com.estudos.pedido.api;

import br.com.estudos.pedido.dominio.Endereco;
import br.com.estudos.pedido.dominio.ItemPedido;
import br.com.estudos.pedido.dominio.OperacaoInvalidaException;
import br.com.estudos.pedido.dominio.Pedido;
import br.com.estudos.pedido.dominio.PedidoNaoEncontradoException;
import br.com.estudos.pedido.servico.PedidoService;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PedidoController.class)
class PedidoControllerTest {

    private static final String PEDIDO_VALIDO = """
            {
              "cliente": "Loja Exemplo",
              "endereco": {"logradouro":"Av. Paulista","numero":"1000","cidade":"São Paulo","uf":"SP","cep":"01310100"},
              "itens": [{"sku":"CAFE-500G","descricao":"Café 500g","quantidade":10}],
              "dataEntregaDesejada": "2099-12-10"
            }
            """;

    @Autowired
    MockMvc mvc;

    @MockitoBean
    PedidoService service;

    private static Pedido pedido() {
        return Pedido.novo("Loja Exemplo", new Endereco("Av. Paulista", "1000", "São Paulo", "SP", "01310100"),
                List.of(new ItemPedido("CAFE-500G", "Café 500g", 10)), LocalDate.of(2099, 12, 10),
                Instant.parse("2026-11-03T12:00:00Z"));
    }

    @Test
    void criaPedidoERetorna201ComLocation() throws Exception {
        Pedido pedido = pedido();
        when(service.criar(any())).thenReturn(pedido);

        mvc.perform(post("/pedidos").contentType(MediaType.APPLICATION_JSON).content(PEDIDO_VALIDO))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost/pedidos/" + pedido.getId()))
                .andExpect(jsonPath("$.status").value("AGUARDANDO_AGENDAMENTO"))
                .andExpect(jsonPath("$.dataEntregaDesejada").value("2099-12-10"));
    }

    @Test
    void pedidoInvalidoRetorna400ComOsCampos() throws Exception {
        String invalido = """
                {
                  "cliente": "",
                  "endereco": {"logradouro":"Av. Paulista","numero":"1000","cidade":"São Paulo","uf":"sp","cep":"123"},
                  "itens": [],
                  "dataEntregaDesejada": "2000-01-01"
                }
                """;

        mvc.perform(post("/pedidos").contentType(MediaType.APPLICATION_JSON).content(invalido))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Dados inválidos"))
                .andExpect(jsonPath("$.campos.cliente").exists())
                .andExpect(jsonPath("$.campos['endereco.uf']").exists())
                .andExpect(jsonPath("$.campos['endereco.cep']").exists())
                .andExpect(jsonPath("$.campos.itens").exists())
                .andExpect(jsonPath("$.campos.dataEntregaDesejada").exists());

        verifyNoInteractions(service);
    }

    @Test
    void pedidoInexistenteRetorna404() throws Exception {
        when(service.buscar("x")).thenThrow(new PedidoNaoEncontradoException("x"));

        mvc.perform(get("/pedidos/x"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Pedido x não encontrado"));
    }

    @Test
    void cancelarPedidoJaCanceladoRetorna422() throws Exception {
        when(service.cancelar(eq("p1"), any())).thenThrow(new OperacaoInvalidaException("Pedido p1 já está cancelado"));

        mvc.perform(post("/pedidos/p1/cancelar").contentType(MediaType.APPLICATION_JSON).content("{\"motivo\":\"teste\"}"))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void cancelarSemMotivoRetorna400() throws Exception {
        mvc.perform(post("/pedidos/p1/cancelar").contentType(MediaType.APPLICATION_JSON).content("{\"motivo\":\"\"}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(service);
    }
}
