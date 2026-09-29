package com.fueldispatch.dispatch.adapter.out.persistence;

import com.fueldispatch.dispatch.domain.FuelType;
import com.fueldispatch.dispatch.domain.OrderStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Row of {@code dispatch_order}. Kept apart from the {@code DispatchOrder} aggregate (API-3.2);
 * {@link OrderJpaMapper} converts between them.
 */
@Entity
@Table(name = "dispatch_order")
class DispatchOrderJpaEntity {

    @Id private UUID id;

    @Column(name = "vessel_name", nullable = false, length = 120)
    private String vesselName;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "vessel_imo", nullable = false, length = 7)
    private String vesselImo;

    @Column(name = "berth", nullable = false, length = 20)
    private String berth;

    @Enumerated(EnumType.STRING)
    @Column(name = "fuel_type", nullable = false, length = 10)
    private FuelType fuelType;

    @Column(name = "quantity_m3", nullable = false, precision = 10, scale = 3)
    private BigDecimal quantityM3;

    @Column(name = "window_start", nullable = false)
    private Instant windowStart;

    @Column(name = "window_end", nullable = false)
    private Instant windowEnd;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private OrderStatus status;

    @Column(name = "cancellation_reason", length = 500)
    private String cancellationReason;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /**
     * Optimistic lock (API-2.4). Null until first persisted, which tells Spring Data to insert
     * instead of merging.
     */
    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    protected DispatchOrderJpaEntity() {}

    DispatchOrderJpaEntity(UUID id) {
        this.id = id;
    }

    UUID getId() {
        return id;
    }

    String getVesselName() {
        return vesselName;
    }

    void setVesselName(String vesselName) {
        this.vesselName = vesselName;
    }

    String getVesselImo() {
        return vesselImo;
    }

    void setVesselImo(String vesselImo) {
        this.vesselImo = vesselImo;
    }

    String getBerth() {
        return berth;
    }

    void setBerth(String berth) {
        this.berth = berth;
    }

    FuelType getFuelType() {
        return fuelType;
    }

    void setFuelType(FuelType fuelType) {
        this.fuelType = fuelType;
    }

    BigDecimal getQuantityM3() {
        return quantityM3;
    }

    void setQuantityM3(BigDecimal quantityM3) {
        this.quantityM3 = quantityM3;
    }

    Instant getWindowStart() {
        return windowStart;
    }

    void setWindowStart(Instant windowStart) {
        this.windowStart = windowStart;
    }

    Instant getWindowEnd() {
        return windowEnd;
    }

    void setWindowEnd(Instant windowEnd) {
        this.windowEnd = windowEnd;
    }

    OrderStatus getStatus() {
        return status;
    }

    void setStatus(OrderStatus status) {
        this.status = status;
    }

    String getCancellationReason() {
        return cancellationReason;
    }

    void setCancellationReason(String cancellationReason) {
        this.cancellationReason = cancellationReason;
    }

    Instant getCreatedAt() {
        return createdAt;
    }

    void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    Instant getUpdatedAt() {
        return updatedAt;
    }

    void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
