package com.fueldispatch.dispatch.adapter.in.web.dto;

import com.fueldispatch.dispatch.domain.CancellationReason;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Body of {@code POST /api/v1/orders/{id}/cancel} (API-2.2, BR-5). */
public record CancelOrderRequest(
        @NotBlank @Size(max = CancellationReason.MAX_LENGTH) String reason) {

    public CancellationReason toReason() {
        return new CancellationReason(reason);
    }
}
