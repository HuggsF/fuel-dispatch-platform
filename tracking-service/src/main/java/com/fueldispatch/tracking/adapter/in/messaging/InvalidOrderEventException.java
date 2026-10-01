package com.fueldispatch.tracking.adapter.in.messaging;

/** A message on the order topic that is not a valid v1 event; no retry can fix it. */
public class InvalidOrderEventException extends RuntimeException {

    InvalidOrderEventException(String message, Throwable cause) {
        super(message, cause);
    }
}
