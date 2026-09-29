-- Transactional outbox (EVT-1.1): events are written in the same transaction as the order and
-- relayed to Kafka later. id = eventId, the value consumers deduplicate on.
create table outbox_event (
    id           uuid        primary key,
    aggregate_id uuid        not null,
    event_type   varchar(50) not null,
    payload      jsonb       not null,
    occurred_at  timestamptz not null,
    published_at timestamptz
);

-- The relay only ever reads unpublished rows, oldest first (EVT-2.1).
create index ix_outbox_event_unpublished on outbox_event (occurred_at) where published_at is null;
