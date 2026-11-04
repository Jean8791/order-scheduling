package br.com.estudos.order.exception;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Filtro que garante um {@code X-Correlation-Id} em toda requisição.
 *
 * <p>{@code OncePerRequestFilter} roda uma vez por requisição. O id vai para a
 * resposta e para os erros, o que ajuda a achar a requisição nos logs. {@code
 * @Order(HIGHEST_PRECEDENCE)} faz ele rodar antes dos outros filtros.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Correlation-Id";
    public static final String PROP = "correlationId";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String cid = request.getHeader(HEADER);
        if (cid == null || cid.isBlank()) {
            cid = UUID.randomUUID().toString();
        }
        request.setAttribute(PROP, cid);
        response.setHeader(HEADER, cid);
        chain.doFilter(request, response);
    }
}
