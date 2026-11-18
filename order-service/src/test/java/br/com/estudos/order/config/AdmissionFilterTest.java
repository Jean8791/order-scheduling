package br.com.estudos.order.config;

import br.com.estudos.order.exception.SecurityErrorHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AdmissionFilterTest {

    private final AdmissionFilter filter = new AdmissionFilter(1, new SecurityErrorHandler(new ObjectMapper()));

    private static MockHttpServletRequest request(String method, String uri) {
        return new MockHttpServletRequest(method, uri);
    }

    /** Executa um pedido; durante ele, chama {@code whileInside} (que representa outro pedido chegando). */
    private MockHttpServletResponse run(MockHttpServletRequest request, Runnable whileInside) throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, (rq, rs) -> whileInside.run());
        return response;
    }

    private MockHttpServletResponse runNested(MockHttpServletRequest nested) {
        try {
            return run(nested, () -> { });
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void refusesWithTooManyRequestsWhenCreationsInProgressReachTheLimit() throws Exception {
        MockHttpServletResponse[] second = new MockHttpServletResponse[1];

        MockHttpServletResponse first = run(request("POST", "/orders"), () -> second[0] = runNested(request("POST", "/orders")));

        assertThat(first.getStatus()).isEqualTo(200);
        assertThat(second[0].getStatus()).isEqualTo(429);
        assertThat(second[0].getHeader("Retry-After")).isEqualTo("1");
        assertThat(second[0].getContentAsString()).contains("TOO_MANY_REQUESTS");
    }

    @Test
    void acceptsTheNextCreationOnceThePreviousOneFinishes() throws Exception {
        run(request("POST", "/orders"), () -> { });

        MockHttpServletResponse next = run(request("POST", "/orders"), () -> { });

        assertThat(next.getStatus()).isEqualTo(200);
    }

    @Test
    void releasesTheSlotWhenTheRequestFails() throws Exception {
        FilterChain failing = (rq, rs) -> {
            throw new IllegalStateException("falhou");
        };
        assertThatThrownBy(() -> filter.doFilter(request("POST", "/orders"), new MockHttpServletResponse(), failing))
                .isInstanceOf(IllegalStateException.class);

        MockHttpServletResponse next = run(request("POST", "/orders"), () -> { });

        assertThat(next.getStatus()).isEqualTo(200);
    }

    @Test
    void doesNotLimitReadsOrOtherRoutes() throws Exception {
        MockHttpServletResponse[] read = new MockHttpServletResponse[1];
        MockHttpServletResponse[] cancel = new MockHttpServletResponse[1];

        run(request("POST", "/orders"), () -> {
            read[0] = runNested(request("GET", "/orders"));
            cancel[0] = runNested(request("POST", "/orders/abc/cancel"));
        });

        assertThat(read[0].getStatus()).isEqualTo(200);
        assertThat(cancel[0].getStatus()).isEqualTo(200);
    }
}
