package br.com.estudos.scheduling.config;

import br.com.estudos.scheduling.entity.DeliverySlot;
import br.com.estudos.scheduling.enums.Period;
import br.com.estudos.scheduling.repository.DeliverySlotRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Cria janelas de entrega de exemplo quando a aplicação sobe com o perfil
 * demo.
 *
 * <p>Mesmo padrão do usuário demo: {@code @Profile("demo")} + {@code
 * ApplicationRunner}. Os valores vêm do application-demo.properties via {@code
 * @Value}.
 */
@Component
@Profile("demo")
public class DemoSlotsLoader implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoSlotsLoader.class);

    private final DeliverySlotRepository repository;
    private final SchedulingProperties properties;
    private final Clock clock;
    private final List<String> states;
    private final int days;
    private final int capacity;

    public DemoSlotsLoader(DeliverySlotRepository repository, SchedulingProperties properties, Clock clock,
                           @Value("${demo.slots.states}") List<String> states,
                           @Value("${demo.slots.days}") int days,
                           @Value("${demo.slots.capacity}") int capacity) {
        this.repository = repository;
        this.properties = properties;
        this.clock = clock;
        this.states = states;
        this.days = days;
        this.capacity = capacity;
    }

    @Override
    public void run(ApplicationArguments args) {
        LocalDate today = LocalDate.now(clock.withZone(properties.zone()));
        Instant now = clock.instant();
        int created = 0;
        for (int offset = 0; offset < days; offset++) {
            LocalDate date = today.plusDays(offset);
            for (String state : states) {
                for (Period period : Period.values()) {
                    if (!repository.exists(state, date, period)) {
                        repository.insert(DeliverySlot.create(state, date, period, capacity, now));
                        created++;
                    }
                }
            }
        }
        log.info("Janelas de demonstração: {} criadas para {} nos próximos {} dias", created, states, days);
    }
}
