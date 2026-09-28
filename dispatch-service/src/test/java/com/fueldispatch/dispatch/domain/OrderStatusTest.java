package com.fueldispatch.dispatch.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

/** DOM-2.5 / BR-4: the transition table of design.md, one row per cell (5 statuses x 4 actions). */
class OrderStatusTest {

    @ParameterizedTest(name = "{0} --{1}--> {2}")
    @CsvSource({
        "CREATED,    APPROVE,  true",
        "CREATED,    DISPATCH, false",
        "CREATED,    DELIVER,  false",
        "CREATED,    CANCEL,   true",
        "APPROVED,   APPROVE,  false",
        "APPROVED,   DISPATCH, true",
        "APPROVED,   DELIVER,  false",
        "APPROVED,   CANCEL,   true",
        "DISPATCHED, APPROVE,  false",
        "DISPATCHED, DISPATCH, false",
        "DISPATCHED, DELIVER,  true",
        "DISPATCHED, CANCEL,   false",
        "DELIVERED,  APPROVE,  false",
        "DELIVERED,  DISPATCH, false",
        "DELIVERED,  DELIVER,  false",
        "DELIVERED,  CANCEL,   false",
        "CANCELLED,  APPROVE,  false",
        "CANCELLED,  DISPATCH, false",
        "CANCELLED,  DELIVER,  false",
        "CANCELLED,  CANCEL,   false"
    })
    void canTransitionTo_eachCellOfTransitionTable_matchesDesign(
            OrderStatus from, OrderAction action, boolean allowed) {
        assertThat(from.canTransitionTo(action.targetStatus())).isEqualTo(allowed);
    }

    @ParameterizedTest
    @CsvSource({
        "APPROVE,  APPROVED",
        "DISPATCH, DISPATCHED",
        "DELIVER,  DELIVERED",
        "CANCEL,   CANCELLED"
    })
    void targetStatus_eachAction_returnsResultingStatus(OrderAction action, OrderStatus target) {
        assertThat(action.targetStatus()).isEqualTo(target);
    }

    @ParameterizedTest
    @EnumSource(
            value = OrderStatus.class,
            names = {"DELIVERED", "CANCELLED"})
    void isTerminal_deliveredOrCancelled_isTrue(OrderStatus status) {
        assertThat(status.isTerminal()).isTrue();
    }

    @ParameterizedTest
    @EnumSource(
            value = OrderStatus.class,
            names = {"CREATED", "APPROVED", "DISPATCHED"})
    void isTerminal_otherStatuses_isFalse(OrderStatus status) {
        assertThat(status.isTerminal()).isFalse();
    }

    @ParameterizedTest
    @EnumSource(OrderStatus.class)
    void canTransitionTo_null_isFalse(OrderStatus status) {
        assertThat(status.canTransitionTo(null)).isFalse();
    }

    @Test
    void values_matchLifecycle() {
        assertThat(OrderStatus.values())
                .containsExactly(
                        OrderStatus.CREATED,
                        OrderStatus.APPROVED,
                        OrderStatus.DISPATCHED,
                        OrderStatus.DELIVERED,
                        OrderStatus.CANCELLED);
    }
}
