package com.fueldispatch.tracking.archfixture.application.port.out;

import reactor.netty.http.client.HttpClient;

/** Violation: Reactor Netty is a web framework, not the Reactor core types ports may use. */
public interface NettyPort {

    HttpClient client();
}
