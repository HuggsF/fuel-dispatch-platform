package com.fueldispatch.dispatch.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/** DOM-1.4 / BR-3 (7-digit IMO) and DOM-1.5 (non-blank name). */
class VesselTest {

    @Test
    void constructor_validNameAndImo_isAccepted() {
        Vessel vessel = new Vessel("Nordic Star", "9321483");

        assertThat(vessel.name()).isEqualTo("Nordic Star");
        assertThat(vessel.imo()).isEqualTo("9321483");
    }

    @Test
    void constructor_surroundingWhitespace_isTrimmed() {
        Vessel vessel = new Vessel("  Nordic Star ", " 9321483 ");

        assertThat(vessel.name()).isEqualTo("Nordic Star");
        assertThat(vessel.imo()).isEqualTo("9321483");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t"})
    void constructor_blankName_throwsDomainValidationException(String name) {
        assertThatThrownBy(() -> new Vessel(name, "9321483"))
                .isInstanceOf(DomainValidationException.class)
                .hasMessageContaining("name");
    }

    /** Includes 6/8 digits, letters, inner spaces, a prefix and non-ASCII (Arabic-Indic) digits. */
    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"932148", "93214830", "93214a3", "932 483", "IMO9321483", "٩٣٢١٤٨٣"})
    void constructor_imoNotExactlySevenDigits_throwsDomainValidationException(String imo) {
        assertThatThrownBy(() -> new Vessel("Nordic Star", imo))
                .isInstanceOf(DomainValidationException.class)
                .hasMessageContaining("IMO");
    }
}
