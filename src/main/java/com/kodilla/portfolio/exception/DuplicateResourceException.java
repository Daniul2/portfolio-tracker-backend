package com.kodilla.portfolio.exception;

/** Thrown when creating something that would violate a uniqueness rule. Maps to HTTP 409. */
public class DuplicateResourceException extends RuntimeException {

    public DuplicateResourceException(String message) {
        super(message);
    }
}
