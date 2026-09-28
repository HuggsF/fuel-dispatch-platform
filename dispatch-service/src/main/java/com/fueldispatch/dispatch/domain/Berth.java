package com.fueldispatch.dispatch.domain;

import java.util.Locale;

/** Mooring position in the terminal (e.g. {@code B-03}). Stored trimmed and upper-case. */
public record Berth(String code) {

    public Berth {
        if (code == null || code.isBlank()) {
            throw new DomainValidationException("berth must not be blank");
        }
        code = code.strip().toUpperCase(Locale.ROOT);
    }
}
