package br.com.estudos.agendamento;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class AgendamentoServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AgendamentoServiceApplication.class, args);
    }
}
