package br.com.estudos.order.config;

import br.com.estudos.order.exception.ErrorCode;
import br.com.estudos.order.exception.SecurityErrorHandler;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.concurrent.Semaphore;

/**
 * Limita quantos {@code POST /orders} correm ao mesmo tempo.
 *
 * <p>Sem limite, uma onda de pedidos enche a fila de conexões do Tomcat e todo mundo espera segundos (ou tem a
 * conexão derrubada). Com o limite, o que passa dele recebe 429 com {@code Retry-After} na hora, e quem foi
 * aceito é atendido sem espera. Roda logo depois do {@code CorrelationIdFilter} e antes do Spring Security, para
 * recusar com o menor custo possível.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class AdmissionFilter extends OncePerRequestFilter {

    private final Semaphore permits;
    private final SecurityErrorHandler errors;

    public AdmissionFilter(@Value("${orders.max-concurrent-creations:64}") int maxConcurrentCreations,
                           SecurityErrorHandler errors) {
        this.permits = new Semaphore(maxConcurrentCreations);
        this.errors = errors;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !("POST".equals(request.getMethod()) && "/orders".equals(request.getRequestURI()));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (!permits.tryAcquire()) {
            response.setHeader("Retry-After", "1");
            errors.write(request, response, ErrorCode.TOO_MANY_REQUESTS, "Muitos pedidos ao mesmo tempo, tente novamente em instantes");
            return;
        }
        try {
            chain.doFilter(request, response);
        } finally {
            permits.release();
        }
    }
}
