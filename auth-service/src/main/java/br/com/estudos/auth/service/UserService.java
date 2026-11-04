package br.com.estudos.auth.service;

import br.com.estudos.auth.dto.user.UserCreateRequest;
import br.com.estudos.auth.dto.user.UserResponse;
import br.com.estudos.auth.entity.User;
import br.com.estudos.auth.exception.BusinessException;
import br.com.estudos.auth.exception.ErrorCode;
import br.com.estudos.auth.mapper.UserMapper;
import br.com.estudos.auth.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Clock;

/**
 * Cadastro e consulta de usuários.
 *
 * <p>A senha nunca é gravada: o {@code PasswordEncoder} (BCrypt) gera um hash
 * com salt, e no login o BCrypt compara a senha digitada com esse hash.
 */
@Service
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    private final UserRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final UserMapper mapper;
    private final Clock clock;

    public UserService(UserRepository repository, PasswordEncoder passwordEncoder, UserMapper mapper, Clock clock) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
        this.mapper = mapper;
        this.clock = clock;
    }

    public UserResponse register(UserCreateRequest request) {
        User user = mapper.toEntity(request, passwordEncoder.encode(request.password()), clock.instant());
        try {
            repository.insert(user);
        } catch (DuplicateKeyException e) {
            throw BusinessException.of(ErrorCode.CONFLICT, "E-mail já cadastrado");
        }
        log.info("Usuário {} cadastrado", user.getEmail());
        return mapper.toResponse(user);
    }

    public UserResponse findByEmail(String email) {
        return repository.findById(mapper.normalizeEmail(email))
                .map(mapper::toResponse)
                .orElseThrow(() -> BusinessException.of(ErrorCode.NOT_FOUND, "Usuário não encontrado"));
    }
}
