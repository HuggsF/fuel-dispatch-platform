package com.fueldispatch.dispatch.application.port.in;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fueldispatch.dispatch.domain.CancellationReason;
import com.fueldispatch.dispatch.domain.OrderAction;
import com.fueldispatch.dispatch.domain.OrderId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class ChangeStatusCommandTest {

    private static final OrderId ID = OrderId.newId();
    private static final CancellationReason REASON = new CancellationReason("Vessel delayed");

    @Test
    void constructor_cancelWithReason_keepsReason() {
        assertThat(new ChangeStatusCommand(ID, OrderAction.CANCEL, REASON).reason())
                .isEqualTo(REASON);
    }

    @Test
    void constructor_cancelWithoutReason_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> new ChangeStatusCommand(ID, OrderAction.CANCEL, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("reason");
    }

    @ParameterizedTest
    @EnumSource(
            value = OrderAction.class,
            names = {"CANCEL"},
            mode = EnumSource.Mode.EXCLUDE)
    void constructor_otherActionWithoutReason_isAccepted(OrderAction action) {
        assertThat(new ChangeStatusCommand(ID, action, null).action()).isEqualTo(action);
    }

    @ParameterizedTest
    @EnumSource(
            value = OrderAction.class,
            names = {"CANCEL"},
            mode = EnumSource.Mode.EXCLUDE)
    void constructor_otherActionWithReason_throwsIllegalArgumentException(OrderAction action) {
        assertThatThrownBy(() -> new ChangeStatusCommand(ID, action, REASON))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("reason");
    }

    @Test
    void constructor_missingOrderIdOrAction_throwsNullPointerException() {
        assertThatThrownBy(() -> new ChangeStatusCommand(null, OrderAction.APPROVE, null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new ChangeStatusCommand(ID, null, null))
                .isInstanceOf(NullPointerException.class);
    }
}
