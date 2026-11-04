package br.com.estudos.auth.service;

import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Proteção contra força bruta no login: depois de {@value #MAX_FAILURES}
 * senhas erradas para o mesmo e-mail dentro de {@link #WINDOW}, o login desse
 * e-mail fica bloqueado até a janela acabar.
 *
 * <p>Os contadores ficam em memória, num {@link ConcurrentHashMap} (seguro para
 * várias requisições ao mesmo tempo). É simples de propósito: reiniciar o
 * serviço zera tudo, e com várias instâncias cada uma conta separado. Em
 * produção de verdade isso iria para um Redis ou para o banco.
 */
@Service
public class LoginAttemptService {

    static final int MAX_FAILURES = 5;
    static final Duration WINDOW = Duration.ofMinutes(15);

    private record Attempts(int failures, Instant firstFailure) {
    }

    private final Map<String, Attempts> attemptsByEmail = new ConcurrentHashMap<>();
    private final Clock clock;

    public LoginAttemptService(Clock clock) {
        this.clock = clock;
    }

    public boolean isBlocked(String email) {
        Attempts attempts = attemptsByEmail.get(email);
        if (attempts == null) {
            return false;
        }
        if (windowExpired(attempts)) {
            attemptsByEmail.remove(email);
            return false;
        }
        return attempts.failures() >= MAX_FAILURES;
    }

    public void registerFailure(String email) {
        attemptsByEmail.compute(email, (key, current) ->
                current == null || windowExpired(current)
                        ? new Attempts(1, clock.instant())
                        : new Attempts(current.failures() + 1, current.firstFailure()));
    }

    public void registerSuccess(String email) {
        attemptsByEmail.remove(email);
    }

    private boolean windowExpired(Attempts attempts) {
        return clock.instant().isAfter(attempts.firstFailure().plus(WINDOW));
    }
}
