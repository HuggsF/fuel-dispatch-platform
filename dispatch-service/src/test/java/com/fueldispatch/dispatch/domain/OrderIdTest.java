package com.fueldispatch.dispatch.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class OrderIdTest {

    @Test
    void newId_calledTwice_returnsDistinctIds() {
        assertThat(OrderId.newId()).isNotEqualTo(OrderId.newId());
    }

    @Test
    void constructor_uuid_isWrappedAndPrintedAsUuid() {
        UUID uuid = UUID.randomUUID();

        assertThat(new OrderId(uuid).value()).isEqualTo(uuid);
        assertThat(new OrderId(uuid)).hasToString(uuid.toString());
    }

    @Test
    void constructor_null_throwsDomainValidationException() {
        assertThatThrownBy(() -> new OrderId(null)).isInstanceOf(DomainValidationException.class);
    }
}
