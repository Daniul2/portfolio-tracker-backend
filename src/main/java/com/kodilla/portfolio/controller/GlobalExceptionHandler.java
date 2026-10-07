package com.kodilla.portfolio.controller;

import com.kodilla.portfolio.dto.CommonDtos.ErrorResponse;
import com.kodilla.portfolio.exception.BusinessRuleException;
import com.kodilla.portfolio.exception.DuplicateResourceException;
import com.kodilla.portfolio.exception.ResourceNotFoundException;
import com.kodilla.portfolio.external.ExternalApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

/** Turns every exception into the same {@link ErrorResponse} shape. */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** Longest parser message passed back to the client; Jackson's can be very long. */
    private static final int MAX_DETAIL_LENGTH = 300;
    private static final String ELLIPSIS = "...";

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(ResourceNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.of(404, "Not Found", e.getMessage()));
    }

    @ExceptionHandler(DuplicateResourceException.class)
    public ResponseEntity<ErrorResponse> handleDuplicate(DuplicateResourceException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of(409, "Conflict", e.getMessage()));
    }

    @ExceptionHandler(BusinessRuleException.class)
    public ResponseEntity<ErrorResponse> handleBusinessRule(BusinessRuleException e) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(ErrorResponse.of(422, "Unprocessable Entity", e.getMessage()));
    }

    /** An upstream provider being down is not the client's fault. */
    @ExceptionHandler(ExternalApiException.class)
    public ResponseEntity<ErrorResponse> handleExternalApi(ExternalApiException e) {
        log.warn("External provider {} failed: {}", e.getProvider(), e.getMessage());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ErrorResponse.of(503, "Service Unavailable",
                        e.getProvider() + " is currently unavailable: " + e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException e) {
        Map<String, String> fieldErrors = new HashMap<>();
        for (FieldError fieldError : e.getBindingResult().getFieldErrors()) {
            fieldErrors.putIfAbsent(fieldError.getField(), fieldError.getDefaultMessage());
        }
        return ResponseEntity.badRequest().body(new ErrorResponse(
                400, "Bad Request", "Request validation failed", fieldErrors, LocalDateTime.now()));
    }

    /**
     * Unparseable body — malformed JSON, or a value that does not fit the target type such as an
     * unknown enum constant.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadableBody(HttpMessageNotReadableException e) {
        return ResponseEntity.badRequest().body(ErrorResponse.of(
                400, "Bad Request", "Request body could not be read: " + rootMessage(e)));
    }

    /** A required query parameter was not supplied. */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingParameter(
            MissingServletRequestParameterException e) {
        return ResponseEntity.badRequest().body(new ErrorResponse(
                400, "Bad Request", "Required parameter is missing",
                Map.of(e.getParameterName(), "parameter of type "
                        + e.getParameterType() + " is required"),
                LocalDateTime.now()));
    }

    /** A path variable or parameter could not be converted, e.g. /v1/users/abc. */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        return ResponseEntity.badRequest().body(new ErrorResponse(
                400, "Bad Request", "Parameter has the wrong type",
                Map.of(String.valueOf(e.getName()), "'" + e.getValue() + "' is not valid here"),
                LocalDateTime.now()));
    }

    /** No mapping and no static file for the requested path — an ordinary 404. */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResource(NoResourceFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ErrorResponse.of(
                404, "Not Found", "No endpoint at " + e.getResourcePath()));
    }

    /** Right path, wrong HTTP method — e.g. DELETE on a collection endpoint. */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotSupported(
            HttpRequestMethodNotSupportedException e) {
        String supported = e.getSupportedHttpMethods() == null ? "" : e.getSupportedHttpMethods()
                .stream().map(Object::toString).collect(Collectors.joining(", "));
        String message = supported.isEmpty()
                ? e.getMethod() + " is not supported here"
                : e.getMethod() + " is not supported here; try " + supported;
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(ErrorResponse.of(405, "Method Not Allowed", message));
    }

    /** Body sent as something other than JSON. */
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleUnsupportedMediaType(
            HttpMediaTypeNotSupportedException e) {
        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                .body(ErrorResponse.of(415, "Unsupported Media Type",
                        "Content-Type " + e.getContentType() + " is not supported; use application/json"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception e) {
        log.error("Unhandled exception", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorResponse.of(500, "Internal Server Error", "An unexpected error occurred"));
    }

    /**
     * Deepest cause message, which for a Jackson failure is the useful part
     * (e.g. which enum value was not recognised).
     */
    private String rootMessage(Throwable throwable) {
        Throwable cause = throwable;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        String message = cause.getMessage();
        if (message == null) {
            return "malformed request";
        }
        // Jackson messages can run to several lines of type detail; keep the first.
        int newline = message.indexOf('\n');
        String firstLine = newline < 0 ? message : message.substring(0, newline);
        return firstLine.length() <= MAX_DETAIL_LENGTH
                ? firstLine
                : firstLine.substring(0, MAX_DETAIL_LENGTH - ELLIPSIS.length()) + ELLIPSIS;
    }
}
