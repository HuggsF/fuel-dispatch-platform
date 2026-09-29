-- jsonb normalises the stored text (key order, whitespace), so the relay would publish a rewritten
-- JSON instead of the mapper's. json still rejects invalid JSON but keeps the text as written, so
-- the Kafka value is byte for byte what DispatchOrderEventMapper produced (EVT-3.1).
alter table outbox_event alter column payload type json using payload::json;
