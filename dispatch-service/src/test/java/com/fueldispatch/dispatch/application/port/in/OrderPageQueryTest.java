package com.fueldispatch.dispatch.application.port.in;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fueldispatch.dispatch.domain.OrderStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class OrderPageQueryTest {

    @Test
    void constructor_limits_areAccepted() {
        assertThat(new OrderPageQuery(null, 0, 1).size()).isEqualTo(1);
        assertThat(new OrderPageQuery(OrderStatus.CREATED, 5, 100).size()).isEqualTo(100);
    }

    @Test
    void constructor_negativePage_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> new OrderPageQuery(null, -1, 20))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("page");
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 101})
    void constructor_sizeOutOfRange_throwsIllegalArgumentException(int size) {
        assertThatThrownBy(() -> new OrderPageQuery(null, 0, size))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("size");
    }
}
