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

        // The MongoDB driver creates each pooled server session id with UUID.randomUUID() while
        // encoding a command on a Netty event loop. On Linux, SecureRandom (NativePRNG) reads
        // /dev/urandom through FileInputStream, which never blocks waiting for entropy and takes
        // microseconds, once per new session. Windows uses another SecureRandom without file I/O,
        // so only Linux (CI) sees this. Scoped to the driver's method, not to UUID.randomUUID().
        builder.allowBlockingCallsInside(
                "com.mongodb.internal.session.ServerSessionPool",
                "createNewServerSessionIdentifier");
    }
}
