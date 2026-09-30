package com.fueldispatch.tracking;

import reactor.blockhound.BlockHound;
import reactor.blockhound.integration.BlockHoundIntegration;

/**
 * Known, bounded blocking inside frameworks that BlockHound (TRK-NF-1) would otherwise report.
 * Loaded through {@code META-INF/services}. Every entry must say why it is safe; our own code gets
 * no exemption.
 */
public class TrackingBlockHoundIntegration implements BlockHoundIntegration {

    @Override
    public void applyTo(BlockHound.Builder builder) {
        // Spring Boot Actuator times every repository call. Its listener resolves the meter
        // registry lazily through SingletonSupplier, which takes a lock only until the value is
        // first computed. The MongoDB driver completes calls on Netty event loops, so two first
        // calls racing at startup can park briefly there once; afterwards get() is lock-free.
        builder.allowBlockingCallsInside(
                "org.springframework.util.function.SingletonSupplier", "get");
    }
}
