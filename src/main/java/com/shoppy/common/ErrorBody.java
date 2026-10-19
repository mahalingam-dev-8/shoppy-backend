package com.shoppy.common;

import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;

/**
 * Error response in the same shape NestJS produced ({@code statusCode, message, error}). The
 * frontend's getErrorMessage reads {@code message}, which may be a string or a list of strings.
 */
public record ErrorBody(int statusCode, Object message, String error) {

    public static ErrorBody of(HttpStatusCode status, Object message) {
        HttpStatus resolved = HttpStatus.resolve(status.value());
        return new ErrorBody(status.value(), message, resolved != null ? resolved.getReasonPhrase() : "Error");
    }
}
