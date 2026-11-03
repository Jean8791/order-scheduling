package br.com.estudos.pedido.api;

import br.com.estudos.pedido.dominio.OperacaoInvalidaException;
import br.com.estudos.pedido.dominio.PedidoNaoEncontradoException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;
import java.util.stream.Collectors;

@RestControllerAdvice
public class TratadorDeErros {

    @ExceptionHandler(PedidoNaoEncontradoException.class)
    public ProblemDetail naoEncontrado(PedidoNaoEncontradoException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(OperacaoInvalidaException.class)
    public ProblemDetail operacaoInvalida(OperacaoInvalidaException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, e.getMessage());
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ProblemDetail conflito(OptimisticLockingFailureException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "O pedido foi alterado por outra operação; tente novamente");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail validacao(MethodArgumentNotValidException e) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Dados inválidos");
        Map<String, String> campos = e.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.toMap(erro -> erro.getField(), erro -> String.valueOf(erro.getDefaultMessage()), (a, b) -> a));
        problema.setProperty("campos", campos);
        return problema;
    }
}
