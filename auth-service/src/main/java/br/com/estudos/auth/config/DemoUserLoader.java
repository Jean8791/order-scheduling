package br.com.estudos.auth.config;

import br.com.estudos.auth.dto.user.UserCreateRequest;
import br.com.estudos.auth.entity.Role;
import br.com.estudos.auth.exception.BusinessException;
import br.com.estudos.auth.exception.ErrorCode;
import br.com.estudos.auth.repository.UserRepository;
import br.com.estudos.auth.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("demo")
public class DemoUserLoader implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoUserLoader.class);

    private final UserService userService;
    private final UserRepository repository;
    private final String name;
    private final String email;
    private final String password;

    public DemoUserLoader(UserService userService, UserRepository repository,
                          @Value("${demo.user.name}") String name,
                          @Value("${demo.user.email}") String email,
                          @Value("${demo.user.password:}") String password) {
        this.userService = userService;
        this.repository = repository;
        this.name = name;
        this.email = email;
        this.password = password;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (password == null || password.isBlank()) {
            log.warn("Perfil demo ativo sem DEMO_USER_PASSWORD: o usuário {} não será criado", email);
            return;
        }
        if (!repository.existsById(email)) {
            try {
                userService.register(new UserCreateRequest(name, email, password));
                log.info("Usuário de demonstração {} criado", email);
            } catch (BusinessException e) {
                // Outra instância criou o mesmo usuário entre o existsById e o insert.
                if (e.getCode() != ErrorCode.CONFLICT) {
                    throw e;
                }
            }
        }
        repository.findById(email).ifPresent(user -> {
            if (user.getRole() != Role.ADMIN) {
                user.promoteToAdmin();
                repository.save(user);
                log.info("Usuário de demonstração {} promovido a ADMIN", email);
            }
        });
    }
}
