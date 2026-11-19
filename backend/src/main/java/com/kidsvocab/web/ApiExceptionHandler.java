package com.kidsvocab.web;

import com.kidsvocab.app.AppException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.NonNull;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.LinkedHashMap;
import java.util.Map;

/** Errors as RFC 7807 problem details with a machine-readable {@code code} property. */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(AppException.class)
    ResponseEntity<ProblemDetail> handleApp(AppException e) {
        HttpStatus status = statusOf(e.code());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, e.getMessage());
        problem.setProperty("code", e.code().name());
        return ResponseEntity.status(status).body(problem);
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            @NonNull MethodArgumentNotValidException e, @NonNull HttpHeaders headers,
            @NonNull HttpStatusCode status, @NonNull WebRequest request) {
        Map<String, String> errors = new LinkedHashMap<>();
        e.getBindingResult().getFieldErrors()
                .forEach(f -> errors.putIfAbsent(f.getField(), f.getDefaultMessage()));
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Request is not valid");
        problem.setProperty("code", AppException.Code.VALIDATION.name());
        problem.setProperty("errors", errors);
        return ResponseEntity.badRequest().body(problem);
    }

    static HttpStatus statusOf(AppException.Code code) {
        return switch (code) {
            case VALIDATION -> HttpStatus.BAD_REQUEST;
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            case CONFLICT -> HttpStatus.CONFLICT;
            case INVALID_CREDENTIALS, INVALID_REFRESH_TOKEN -> HttpStatus.UNAUTHORIZED;
            case INVALID_PIN, FORBIDDEN -> HttpStatus.FORBIDDEN;
            case PIN_LOCKED -> HttpStatus.TOO_MANY_REQUESTS;
        };
    }
}
