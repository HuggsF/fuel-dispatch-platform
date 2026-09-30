package com.fueldispatch.tracking.domain;

/** Thrown when tracking data breaks an invariant of the domain model. */
public class DomainValidationException extends RuntimeException {

    public DomainValidationException(String message) {
        super(message);
    }
}
