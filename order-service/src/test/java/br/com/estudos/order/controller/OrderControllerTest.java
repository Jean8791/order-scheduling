package br.com.estudos.order.controller;

import br.com.estudos.order.dto.order.OrderResponse;
import br.com.estudos.order.entity.Address;
import br.com.estudos.order.entity.Order;
import br.com.estudos.order.entity.OrderItem;
import br.com.estudos.order.exception.BusinessException;
import br.com.estudos.order.exception.ErrorCode;
import br.com.estudos.order.mapper.OrderMapper;
import br.com.estudos.order.service.OrderService;
import br.com.estudos.order.config.SecurityConfig;
import br.com.estudos.order.exception.SecurityErrorHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
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

@WebMvcTest(OrderController.class)
@Import({SecurityConfig.class, SecurityErrorHandler.class})
@WithMockUser
class OrderControllerTest {

    private static final String VALID_ORDER = """
            {
              "customer": "Loja Exemplo",
              "address": {"street":"Av. Paulista","number":"1000","city":"São Paulo","state":"SP","zipCode":"01310100"},
              "items": [{"sku":"CAFE-500G","description":"Café 500g","quantity":10}],
              "desiredDeliveryDate": "2099-12-10"
            }
            """;

    @Autowired
    MockMvc mvc;

    @MockitoBean
    OrderService service;

    @MockitoBean
    JwtDecoder jwtDecoder;

    private static OrderResponse order() {
        return new OrderMapper().toResponse(Order.create("Loja Exemplo", new Address("Av. Paulista", "1000", "São Paulo", "SP", "01310100"),
                List.of(new OrderItem("CAFE-500G", "Café 500g", 10)), LocalDate.of(2099, 12, 10),
                Instant.parse("2026-11-03T12:00:00Z")));
    }

    @Test
    void createsOrderAndReturns201WithLocation() throws Exception {
        OrderResponse order = order();
        when(service.create(any())).thenReturn(order);

        mvc.perform(post("/orders").contentType(MediaType.APPLICATION_JSON).content(VALID_ORDER))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost/orders/" + order.id()))
                .andExpect(jsonPath("$.status").value("AWAITING_SCHEDULING"))
                .andExpect(jsonPath("$.desiredDeliveryDate").value("2099-12-10"));
    }

    @Test
    void invalidOrderReturns400WithFieldErrors() throws Exception {
        String invalid = """
                {
                  "customer": "",
                  "address": {"street":"Av. Paulista","number":"1000","city":"São Paulo","state":"sp","zipCode":"123"},
                  "items": [],
                  "desiredDeliveryDate": "2000-01-01"
                }
                """;

        mvc.perform(post("/orders").contentType(MediaType.APPLICATION_JSON).content(invalid))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.message").value("Falha de validação"))
                .andExpect(jsonPath("$.path").value("/orders"))
                .andExpect(jsonPath("$.fieldErrors[?(@.field == 'customer')]").exists())
                .andExpect(jsonPath("$.fieldErrors[?(@.field == 'address.state')].rejectedValue").value("sp"))
                .andExpect(jsonPath("$.fieldErrors[?(@.field == 'address.zipCode')]").exists())
                .andExpect(jsonPath("$.fieldErrors[?(@.field == 'items')]").exists())
                .andExpect(jsonPath("$.fieldErrors[?(@.field == 'desiredDeliveryDate')]").exists());

        verifyNoInteractions(service);
    }

    @Test
    void missingOrderReturns404() throws Exception {
        when(service.findById("x")).thenThrow(BusinessException.of(ErrorCode.NOT_FOUND, "Pedido x não encontrado"));

        mvc.perform(get("/orders/x"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Pedido x não encontrado"))
                .andExpect(jsonPath("$.fieldErrors").isEmpty());
    }

    @Test
    void cancelingAlreadyCanceledOrderReturns422() throws Exception {
        when(service.cancel(eq("p1"), any())).thenThrow(BusinessException.of(ErrorCode.UNPROCESSABLE, "Pedido p1 já está cancelado"));

        mvc.perform(post("/orders/p1/cancel").contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"teste\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error").value("Unprocessable Entity"))
                .andExpect(jsonPath("$.code").value("UNPROCESSABLE"));
    }

    @Test
    void cancelWithoutReasonReturns400() throws Exception {
        mvc.perform(post("/orders/p1/cancel").contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"\"}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(service);
    }

    @Test
    void echoesReceivedCorrelationIdInHeaderAndBody() throws Exception {
        when(service.findById("x")).thenThrow(BusinessException.of(ErrorCode.NOT_FOUND, "Pedido x não encontrado"));

        mvc.perform(get("/orders/x").header("X-Correlation-Id", "abc-123"))
                .andExpect(header().string("X-Correlation-Id", "abc-123"))
                .andExpect(jsonPath("$.correlationId").value("abc-123"));
    }

    @Test
    void generatesCorrelationIdWhenMissing() throws Exception {
        when(service.findById("x")).thenThrow(BusinessException.of(ErrorCode.NOT_FOUND, "Pedido x não encontrado"));

        mvc.perform(get("/orders/x"))
                .andExpect(header().exists("X-Correlation-Id"))
                .andExpect(jsonPath("$.correlationId").isNotEmpty());
    }

    @Test
    void malformedJsonReturns400InTheSameFormat() throws Exception {
        mvc.perform(post("/orders").contentType(MediaType.APPLICATION_JSON).content("{nao é json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.message").value("Requisição inválida"));

        verifyNoInteractions(service);
    }

    @Test
    void unexpectedErrorReturns500WithoutInternalDetails() throws Exception {
        when(service.findById("x")).thenThrow(new IllegalStateException("internal detail"));

        mvc.perform(get("/orders/x"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.message").value("Erro interno inesperado"));
    }

    @Test
    @WithAnonymousUser
    void requestWithoutTokenReturns401() throws Exception {
        mvc.perform(get("/orders/x"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", "Bearer"))
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.message").value("Token ausente, inválido ou expirado"))
                .andExpect(jsonPath("$.correlationId").isNotEmpty());

        verifyNoInteractions(service);
    }
}
