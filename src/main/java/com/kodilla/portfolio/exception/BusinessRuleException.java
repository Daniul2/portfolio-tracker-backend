package com.kodilla.portfolio.exception;

/**
 * Thrown when a request is well-formed but breaks a domain rule, such as
 * selling more of an asset than the portfolio holds. Maps to HTTP 422.
 */
public class BusinessRuleException extends RuntimeException {

    public BusinessRuleException(String message) {
        super(message);
    }
}
