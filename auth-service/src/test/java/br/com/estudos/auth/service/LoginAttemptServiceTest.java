package br.com.estudos.auth.service;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class LoginAttemptServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-03T12:00:00Z");
    private static final String EMAIL = "ana@email.com";

    @Test
    void blocksAfterTooManyFailures() {
        LoginAttemptService service = new LoginAttemptService(Clock.fixed(NOW, ZoneOffset.UTC));

        for (int i = 0; i < LoginAttemptService.MAX_FAILURES - 1; i++) {
            service.registerFailure(EMAIL);
        }
        assertThat(service.isBlocked(EMAIL)).isFalse();

        service.registerFailure(EMAIL);
        assertThat(service.isBlocked(EMAIL)).isTrue();
        assertThat(service.isBlocked("outra@email.com")).isFalse();
    }

    @Test
    void unblocksWhenTheWindowEnds() {
        MovableClock clock = new MovableClock(NOW);
        LoginAttemptService service = new LoginAttemptService(clock);
        for (int i = 0; i < LoginAttemptService.MAX_FAILURES; i++) {
            service.registerFailure(EMAIL);
        }
        assertThat(service.isBlocked(EMAIL)).isTrue();

        clock.now = NOW.plus(LoginAttemptService.WINDOW).plusSeconds(1);

        assertThat(service.isBlocked(EMAIL)).isFalse();
    }

    @Test
    void successfulLoginClearsTheFailures() {
        LoginAttemptService service = new LoginAttemptService(Clock.fixed(NOW, ZoneOffset.UTC));
        for (int i = 0; i < LoginAttemptService.MAX_FAILURES; i++) {
            service.registerFailure(EMAIL);
        }

        service.registerSuccess(EMAIL);

        assertThat(service.isBlocked(EMAIL)).isFalse();
    }

    private static final class MovableClock extends Clock {

        private Instant now;

        private MovableClock(Instant now) {
            this.now = now;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
