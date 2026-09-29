package com.fueldispatch.dispatch.domain;

import java.util.regex.Pattern;

/**
 * Ship receiving fuel. The name must not be blank (DOM-1.5) and the IMO number must be exactly 7
 * ASCII digits (BR-3). Both are stored trimmed.
 */
public record Vessel(String name, String imo) {

    private static final Pattern IMO_PATTERN = Pattern.compile("[0-9]{7}");

    public Vessel {
        if (name == null || name.isBlank()) {
            throw new DomainValidationException("vessel name must not be blank");
        }
        if (imo == null || !IMO_PATTERN.matcher(imo.strip()).matches()) {
            throw new DomainValidationException("vessel IMO number must be exactly 7 digits");
        }
        name = name.strip();
        imo = imo.strip();
    }
}
