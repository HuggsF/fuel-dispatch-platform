package com.fueldispatch.tracking.archfixture.adapter.in.messaging;

import com.fueldispatch.dispatch.standin.DispatchEventStandIn;

/** Violation (TRK-4.2): tracking code depending on dispatch-service code. */
public class DispatchCodeConsumer {

    public void consume(DispatchEventStandIn event) {}
}
