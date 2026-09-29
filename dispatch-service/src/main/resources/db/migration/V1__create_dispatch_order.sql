-- Dispatch orders (API-3.1). IDs come from the domain; the schema is owned by Flyway only.
create table dispatch_order (
    id                  uuid           primary key,
    vessel_name         varchar(120)   not null,
    vessel_imo          char(7)        not null,
    berth               varchar(20)    not null,
    fuel_type           varchar(10)    not null,
    quantity_m3         numeric(10, 3) not null,
    window_start        timestamptz    not null,
    window_end          timestamptz    not null,
    status              varchar(20)    not null,
    cancellation_reason varchar(500),
    created_at          timestamptz    not null,
    updated_at          timestamptz    not null,
    version             bigint         not null,
    constraint ck_dispatch_order_quantity_positive check (quantity_m3 > 0),
    constraint ck_dispatch_order_window_end_after_start check (window_end > window_start)
);

create index ix_dispatch_order_status on dispatch_order (status);
create index ix_dispatch_order_created_at on dispatch_order (created_at desc);
