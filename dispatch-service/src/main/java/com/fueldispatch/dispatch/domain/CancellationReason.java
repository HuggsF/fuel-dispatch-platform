package com.fueldispatch.dispatch.domain;

/**
 * Why an order was cancelled (BR-5): not blank and at most 500 characters after trimming. Stored
 * trimmed.
 */
public record CancellationReason(String value) {

    public static final int MAX_LENGTH = 500;

    public CancellationReason {
        if (value == null || value.isBlank()) {
            throw new DomainValidationException("cancellation reason must not be blank");
        }
        value = value.strip();
        if (value.length() > MAX_LENGTH) {
            throw new DomainValidationException(
                    "cancellation reason must be at most " + MAX_LENGTH + " characters");
        }
    }
}
