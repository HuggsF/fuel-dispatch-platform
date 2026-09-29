package com.fueldispatch.dispatch.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.fueldispatch.dispatch.domain.OrderId;
import org.junit.jupiter.api.Test;

class OrderNotFoundExceptionTest {

    @Test
    void constructor_orderId_isExposedAndNamedInMessage() {
        OrderId id = OrderId.newId();

        OrderNotFoundException exception = new OrderNotFoundException(id);

        assertThat(exception.orderId()).isEqualTo(id);
        assertThat(exception).hasMessage("order " + id + " not found");
    }
}
