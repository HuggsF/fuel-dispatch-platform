package com.fueldispatch.dispatch.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/** DOM-1.5: the berth must not be blank; it is stored trimmed and upper-case. */
class BerthTest {

    @Test
    void constructor_validCode_isAccepted() {
        assertThat(new Berth("B-03").code()).isEqualTo("B-03");
    }

    @Test
    void constructor_lowerCaseWithWhitespace_isTrimmedAndUpperCased() {
        assertThat(new Berth("  b-03 ").code()).isEqualTo("B-03");
    }

    @Test
    void equals_differentCaseAndWhitespace_isEqual() {
        assertThat(new Berth(" b-03")).isEqualTo(new Berth("B-03"));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\n"})
    void constructor_blankCode_throwsDomainValidationException(String code) {
        assertThatThrownBy(() -> new Berth(code))
                .isInstanceOf(DomainValidationException.class)
                .hasMessageContaining("berth");
    }
}
