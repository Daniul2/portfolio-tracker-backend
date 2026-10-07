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
        return respond(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(DuplicateResourceException.class)
    public ResponseEntity<ErrorResponse> handleDuplicate(DuplicateResourceException e) {
        return respond(HttpStatus.CONFLICT, e.getMessage());
    }

    @ExceptionHandler(BusinessRuleException.class)
    public ResponseEntity<ErrorResponse> handleBusinessRule(BusinessRuleException e) {
        return respond(HttpStatus.UNPROCESSABLE_ENTITY, e.getMessage());
    }

    /** An upstream provider being down is not the client's fault. */
    @ExceptionHandler(ExternalApiException.class)
    public ResponseEntity<ErrorResponse> handleExternalApi(ExternalApiException e) {
        log.warn("External provider {} failed: {}", e.getProvider(), e.getMessage());
        return respond(HttpStatus.SERVICE_UNAVAILABLE,
                e.getProvider() + " is currently unavailable: " + e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException e) {
        Map<String, String> fieldErrors = new HashMap<>();
        for (FieldError fieldError : e.getBindingResult().getFieldErrors()) {
            fieldErrors.putIfAbsent(fieldError.getField(), fieldError.getDefaultMessage());
        }
        return respond(HttpStatus.BAD_REQUEST, "Request validation failed", fieldErrors);
    }

    /**
     * Unparseable body — malformed JSON, or a value that does not fit the target type such as an
     * unknown enum constant.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadableBody(HttpMessageNotReadableException e) {
        return respond(HttpStatus.BAD_REQUEST, "Request body could not be read: " + rootMessage(e));
    }

    /** A required query parameter was not supplied. */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingParameter(
            MissingServletRequestParameterException e) {
        return respond(HttpStatus.BAD_REQUEST, "Required parameter is missing",
                Map.of(e.getParameterName(), "parameter of type " + e.getParameterType() + " is required"));
    }

    /** A path variable or parameter could not be converted, e.g. /v1/users/abc. */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        return respond(HttpStatus.BAD_REQUEST, "Parameter has the wrong type",
                Map.of(String.valueOf(e.getName()), "'" + e.getValue() + "' is not valid here"));
    }

    /** No mapping and no static file for the requested path — an ordinary 404. */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResource(NoResourceFoundException e) {
        return respond(HttpStatus.NOT_FOUND, "No endpoint at " + e.getResourcePath());
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
        return respond(HttpStatus.METHOD_NOT_ALLOWED, message);
    }

    /** Body sent as something other than JSON. */
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleUnsupportedMediaType(
            HttpMediaTypeNotSupportedException e) {
        return respond(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                "Content-Type " + e.getContentType() + " is not supported; use application/json");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception e) {
        log.error("Unhandled exception", e);
        return respond(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred");
    }

    /** The HTTP status is the single source for both the response code and the body. */
    private static ResponseEntity<ErrorResponse> respond(HttpStatus status, String message) {
        return respond(status, message, Map.of());
    }

    private static ResponseEntity<ErrorResponse> respond(HttpStatus status, String message,
                                                         Map<String, String> fieldErrors) {
        return ResponseEntity.status(status).body(new ErrorResponse(
                status.value(), status.getReasonPhrase(), message, fieldErrors, LocalDateTime.now()));
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
