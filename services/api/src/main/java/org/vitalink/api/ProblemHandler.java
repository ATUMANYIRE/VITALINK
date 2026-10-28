package org.vitalink.api;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.net.URI;
import org.springframework.dao.DataAccessException;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class ProblemHandler {
    @ExceptionHandler(ApiProblem.class)
    ResponseEntity<ProblemDetail> known(ApiProblem error, HttpServletRequest request) {
        return response(error.status(), error.code(), error.getMessage(), request.getRequestURI());
    }
    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class,
        MethodArgumentTypeMismatchException.class, ConstraintViolationException.class,
        org.springframework.web.bind.MissingRequestHeaderException.class,
        org.springframework.web.method.annotation.HandlerMethodValidationException.class})
    ResponseEntity<ProblemDetail> invalid(Exception ignored, HttpServletRequest request) {
        return response(400,"INVALID_INPUT","Request format or field values are invalid.",request.getRequestURI());
    }
    @ExceptionHandler(DataAccessException.class)
    ResponseEntity<ProblemDetail> database(Exception ignored, HttpServletRequest request) {
        return response(503,"SERVICE_UNAVAILABLE","Service temporarily unavailable; retry using the same referral identifier.",request.getRequestURI());
    }
    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> unexpected(Exception ignored, HttpServletRequest request) {
        return response(500,"INTERNAL_ERROR","An unexpected error occurred.",request.getRequestURI());
    }
    static ResponseEntity<ProblemDetail> response(int status,String code,String detail,String path) {
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.valueOf(status),detail);
        problem.setType(URI.create("urn:vitalink:error:"+code.toLowerCase(java.util.Locale.ROOT)));
        problem.setTitle(HttpStatus.valueOf(status).getReasonPhrase());
        problem.setProperty("code",code);
        problem.setInstance(URI.create(path));
        return ResponseEntity.status(status).cacheControl(CacheControl.noStore()).body(problem);
    }
}
