-- Serves the relay's head-of-line check (EVT-2.6): "is there an older unpublished event of this
-- order?" stays an index lookup even when Kafka is down and the outbox grows.
create index ix_outbox_event_unpublished_by_aggregate
    on outbox_event (aggregate_id, occurred_at)
    where published_at is null;
