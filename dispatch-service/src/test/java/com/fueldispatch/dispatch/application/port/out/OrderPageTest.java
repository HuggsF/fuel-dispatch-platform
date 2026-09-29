package com.fueldispatch.dispatch.application.port.out;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fueldispatch.dispatch.domain.DispatchOrder;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class OrderPageTest {

    @Test
    void constructor_items_areCopiedAndReadOnly() {
        List<DispatchOrder> items = new ArrayList<>();

        OrderPage page = new OrderPage(items, 0, 20, 0);
        items.add(null);

        assertThat(page.items()).isEmpty();
        assertThatThrownBy(() -> page.items().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
