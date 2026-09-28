package com.fueldispatch.dispatch.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/** DOM-2.6 / BR-5: the reason is mandatory and at most 500 characters. */
class CancellationReasonTest {

    @Test
    void constructor_validReason_isAccepted() {
        assertThat(new CancellationReason("Vessel delayed").value()).isEqualTo("Vessel delayed");
    }

    @Test
    void constructor_surroundingWhitespace_isTrimmed() {
        assertThat(new CancellationReason("  Vessel delayed \n").value())
                .isEqualTo("Vessel delayed");
    }

    @Test
    void constructor_exactly500Characters_isAccepted() {
        assertThat(new CancellationReason("x".repeat(500)).value()).hasSize(500);
    }

    @Test
    void constructor_500CharactersPlusSurroundingWhitespace_isAccepted() {
        assertThat(new CancellationReason(" " + "x".repeat(500) + " ").value()).hasSize(500);
    }

    @Test
    void constructor_501Characters_throwsDomainValidationException() {
        String reason = "x".repeat(501);

        assertThatThrownBy(() -> new CancellationReason(reason))
                .isInstanceOf(DomainValidationException.class)
                .hasMessageContaining("500");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t\n"})
    void constructor_blankReason_throwsDomainValidationException(String reason) {
        assertThatThrownBy(() -> new CancellationReason(reason))
                .isInstanceOf(DomainValidationException.class)
                .hasMessageContaining("reason");
    }
}
