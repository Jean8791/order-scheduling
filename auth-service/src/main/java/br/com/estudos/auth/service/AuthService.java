package br.com.estudos.auth.service;

import br.com.estudos.auth.dto.auth.LoginRequest;
import br.com.estudos.auth.dto.auth.LoginResponse;
import br.com.estudos.auth.entity.User;
import br.com.estudos.auth.exception.BusinessException;
import br.com.estudos.auth.exception.ErrorCode;
import br.com.estudos.auth.mapper.UserMapper;
import br.com.estudos.auth.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;
    private final UserMapper mapper;

    public AuthService(UserRepository repository, PasswordEncoder passwordEncoder, TokenService tokenService, UserMapper mapper) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
        this.mapper = mapper;
    }

    public LoginResponse login(LoginRequest request) {
        User user = repository.findById(mapper.normalizeEmail(request.email()))
                .filter(found -> passwordEncoder.matches(request.password(), found.getPasswordHash()))
                .orElseThrow(() -> BusinessException.of(ErrorCode.UNAUTHORIZED, "E-mail ou senha inválidos"));
        log.info("Login do usuário {}", user.getEmail());
        return mapper.toLoginResponse(user, tokenService.generate(user));
    }
}
