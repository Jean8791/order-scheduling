package br.com.estudos.auth;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Ponto de entrada do Spring Boot.
 *
 * <p>{@code @SpringBootApplication} junta três coisas: {@code @Configuration}
 * (esta classe pode declarar beans), {@code @EnableAutoConfiguration} (o
 * Spring configura sozinho o que encontra no classpath: servidor web, MongoDB,
 * RabbitMQ, segurança...) e {@code @ComponentScan} (procura classes anotadas
 * com {@code @Component}, {@code @Service}, {@code @RestController} etc. neste
 * pacote e nos de baixo e cria uma instância de cada uma).
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class AuthServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AuthServiceApplication.class, args);
    }
}
