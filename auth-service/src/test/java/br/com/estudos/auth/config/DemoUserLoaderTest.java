package br.com.estudos.auth.config;

import br.com.estudos.auth.dto.user.UserCreateRequest;
import br.com.estudos.auth.exception.BusinessException;
import br.com.estudos.auth.exception.ErrorCode;
import br.com.estudos.auth.repository.UserRepository;
import br.com.estudos.auth.service.UserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DemoUserLoaderTest {

    private static final String EMAIL = "demo@orderscheduling.dev";

    @Mock
    UserService userService;

    @Mock
    UserRepository repository;

    private DemoUserLoader loader(String password) {
        return new DemoUserLoader(userService, repository, "Usuário Demo", EMAIL, password);
    }

    @Test
    void withoutPasswordDoesNotCreateUser() {
        loader("").run(null);

        verifyNoInteractions(userService, repository);
    }

    @Test
    void createsUserWhenItDoesNotExist() {
        when(repository.existsById(EMAIL)).thenReturn(false);

        loader("senha-demo-123").run(null);

        verify(userService).register(new UserCreateRequest("Usuário Demo", EMAIL, "senha-demo-123"));
    }

    @Test
    void existingUserIsKept() {
        when(repository.existsById(EMAIL)).thenReturn(true);

        loader("senha-demo-123").run(null);

        verify(userService, never()).register(any());
    }

    @Test
    void concurrentCreationIsTreatedAsAlreadyExisting() {
        when(repository.existsById(EMAIL)).thenReturn(false);
        when(userService.register(any())).thenThrow(BusinessException.of(ErrorCode.CONFLICT, "E-mail já cadastrado"));

        assertThatCode(() -> loader("senha-demo-123").run(null)).doesNotThrowAnyException();
    }
}
