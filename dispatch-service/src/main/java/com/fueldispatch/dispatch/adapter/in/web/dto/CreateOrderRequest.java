package com.fueldispatch.dispatch.adapter.in.web.dto;

import com.fueldispatch.dispatch.application.port.in.CreateOrderCommand;
import com.fueldispatch.dispatch.domain.Berth;
import com.fueldispatch.dispatch.domain.DeliveryWindow;
import com.fueldispatch.dispatch.domain.FuelType;
import com.fueldispatch.dispatch.domain.Quantity;
import com.fueldispatch.dispatch.domain.Vessel;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * Body of {@code POST /api/v1/orders}. Repeats the domain rules (BR-1 to BR-3) and the column sizes
 * so a 400 lists every invalid field at once (API-1.2).
 */
@WindowEndAfterStart
public record CreateOrderRequest(
        @NotBlank @Size(max = 120) String vesselName,
        @NotNull @Pattern(regexp = "[0-9]{7}", message = "must be exactly 7 digits") String vesselImo,
        @NotBlank @Size(max = 20) String berth,
        @NotNull @Pattern(regexp = "MGO|VLSFO|HFO", message = "must be one of MGO, VLSFO, HFO") String fuelType,
        @NotNull @DecimalMin(value = "0", inclusive = false) @DecimalMax("10000") @MaxDecimalPlaces(Quantity.SCALE)
                BigDecimal quantityM3,
        @NotNull Instant windowStart,
        @NotNull Instant windowEnd) {

    public CreateOrderCommand toCommand() {
        return new CreateOrderCommand(
                new Vessel(vesselName, vesselImo),
                new Berth(berth),
                FuelType.valueOf(fuelType),
                new Quantity(quantityM3),
                new DeliveryWindow(windowStart, windowEnd));
    }
}
