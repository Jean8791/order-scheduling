package br.com.estudos.scheduling.controller;

import br.com.estudos.scheduling.entity.DeliverySlot;
import br.com.estudos.scheduling.enums.Period;
import br.com.estudos.scheduling.exception.BusinessException;
import br.com.estudos.scheduling.exception.ErrorCode;
import br.com.estudos.scheduling.mapper.DeliverySlotMapper;
import br.com.estudos.scheduling.service.DeliverySlotService;
import br.com.estudos.scheduling.config.SecurityConfig;
import br.com.estudos.scheduling.exception.SecurityErrorHandler;
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
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DeliverySlotController.class)
@Import({SecurityConfig.class, SecurityErrorHandler.class})
@WithMockUser
class DeliverySlotControllerTest {

    private static final DeliverySlotMapper MAPPER = new DeliverySlotMapper();

    private static final String SLOT = """
            {"state":"SP","date":"2099-12-10","period":"MORNING","capacity":2}
            """;

    @Autowired
    MockMvc mvc;

    @MockitoBean
    DeliverySlotService service;

    @MockitoBean
    JwtDecoder jwtDecoder;

    @Test
    void createsSlotAndReturns201() throws Exception {
        when(service.create(any())).thenReturn(MAPPER.toResponse(DeliverySlot.create("SP", LocalDate.of(2099, 12, 10), Period.MORNING, 2, Instant.EPOCH)));

        mvc.perform(post("/delivery-slots").contentType(MediaType.APPLICATION_JSON).content(SLOT))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.state").value("SP"))
                .andExpect(jsonPath("$.capacity").value(2))
                .andExpect(jsonPath("$.available").value(2));
    }

    @Test
    void duplicatedSlotReturns409() throws Exception {
        when(service.create(any())).thenThrow(BusinessException.of(ErrorCode.CONFLICT, "Já existe janela para SP em 2099-12-10 no período MORNING"));

        mvc.perform(post("/delivery-slots").contentType(MediaType.APPLICATION_JSON).content(SLOT))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"))
                .andExpect(jsonPath("$.message").value("Já existe janela para SP em 2099-12-10 no período MORNING"));
    }

    @Test
    void zeroCapacityAndInvalidPeriodReturn400() throws Exception {
        mvc.perform(post("/delivery-slots").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"state\":\"SP\",\"date\":\"2099-12-10\",\"period\":\"MORNING\",\"capacity\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.fieldErrors[?(@.field == 'capacity')].rejectedValue").value("0"));

        mvc.perform(post("/delivery-slots").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"state\":\"SP\",\"date\":\"2099-12-10\",\"period\":\"NIGHT\",\"capacity\":1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));

        verifyNoInteractions(service);
    }

    @Test
    void listsSlotsInRange() throws Exception {
        when(service.list("SP", LocalDate.of(2099, 12, 10), LocalDate.of(2099, 12, 17)))
                .thenReturn(List.of(MAPPER.toResponse(DeliverySlot.create("SP", LocalDate.of(2099, 12, 10), Period.AFTERNOON, 3, Instant.EPOCH))));

        mvc.perform(get("/delivery-slots").param("state", "SP").param("from", "2099-12-10").param("to", "2099-12-17"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].period").value("AFTERNOON"))
                .andExpect(jsonPath("$[0].available").value(3));
    }

    @Test
    void listWithoutRequiredParameterReturns400() throws Exception {
        mvc.perform(get("/delivery-slots?from=2099-12-10"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.path").value("/delivery-slots?from=2099-12-10"));
    }

    @Test
    @WithAnonymousUser
    void requestWithoutTokenReturns401() throws Exception {
        mvc.perform(post("/delivery-slots").contentType(MediaType.APPLICATION_JSON).content(SLOT))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.message").value("Token ausente, inválido ou expirado"));

        verifyNoInteractions(service);
    }
}
