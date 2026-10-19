package com.shoppy.common;

import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    ResponseEntity<ErrorBody> handleApi(ApiException ex) {
        return respond(ex.getStatus(), ex.getMessage());
    }

    /** Bean Validation failures: every message, as NestJS's ValidationPipe returned them. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ErrorBody> handleValidation(MethodArgumentNotValidException ex) {
        List<String> messages = ex.getBindingResult().getAllErrors().stream()
                .map(error -> Objects.requireNonNullElse(error.getDefaultMessage(), "invalid value"))
                .toList();
        return respond(HttpStatus.BAD_REQUEST, messages);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ErrorBody> handleUnreadable(HttpMessageNotReadableException ex) {
        return respond(HttpStatus.BAD_REQUEST, "Request body is missing or malformed");
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<ErrorBody> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return respond(HttpStatus.BAD_REQUEST, "Invalid value for " + ex.getName());
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ResponseEntity<ErrorBody> handleTooLarge(MaxUploadSizeExceededException ex) {
        return respond(HttpStatus.BAD_REQUEST, "Validation failed (expected size is less than 2000000)");
    }

    /** Thrown by @PreAuthorize; without this the catch-all below would turn it into a 500. */
    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ErrorBody> handleAccessDenied(AccessDeniedException ex) {
        return respond(HttpStatus.FORBIDDEN, "Forbidden resource");
    }

    /**
     * Spring's own MVC exceptions (unknown route, wrong method, missing part or header) carry
     * their status; anything else is a real server error.
     */
    @ExceptionHandler(Exception.class)
    ResponseEntity<ErrorBody> handleOther(Exception ex) {
        if (ex instanceof ErrorResponse errorResponse) {
            HttpStatusCode status = errorResponse.getStatusCode();
            String detail = errorResponse.getBody().getDetail();
            return respond(status, detail != null ? detail : ex.getMessage());
        }
        log.error("Unhandled error", ex);
        return respond(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error");
    }

    private static ResponseEntity<ErrorBody> respond(HttpStatusCode status, Object message) {
        return ResponseEntity.status(status).body(ErrorBody.of(status, message));
    }
}
