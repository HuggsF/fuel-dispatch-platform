package com.fueldispatch.dispatch.domain;

/** Thrown when input violates a domain rule (e.g. BR-1, BR-2, BR-3, BR-5). */
public class DomainValidationException extends RuntimeException {

    public DomainValidationException(String message) {
        super(message);
    }
}
