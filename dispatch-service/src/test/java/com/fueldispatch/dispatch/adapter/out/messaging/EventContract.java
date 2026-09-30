package com.fueldispatch.dispatch.adapter.out.messaging;

import com.fasterxml.jackson.databind.JsonNode;
import com.networknt.schema.Error;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SchemaRegistryConfig;
import com.networknt.schema.SpecificationVersion;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * The versioned event contract in {@code contracts/} (EVT-3.1), loaded for tests. Tests run with
 * the module directory as working directory, so the contracts live one level up.
 */
final class EventContract {

    static final Path CONTRACTS_DIR = Path.of("..", "contracts");
    static final Path EXAMPLES_DIR = CONTRACTS_DIR.resolve("examples");
    static final Path SCHEMA_V1 = CONTRACTS_DIR.resolve("dispatch-order-event.v1.schema.json");

    private final Schema schema;

    private EventContract(Schema schema) {
        this.schema = schema;
    }

    static EventContract v1() {
        // Format assertions are off by default in 2020-12; the contract relies on uuid and
        // date-time, so turn them on.
        SchemaRegistry registry =
                SchemaRegistry.withDefaultDialect(
                        SpecificationVersion.DRAFT_2020_12,
                        builder ->
                                builder.schemaRegistryConfig(
                                        SchemaRegistryConfig.builder()
                                                .formatAssertionsEnabled(true)
                                                .build()));
        try (InputStream in = Files.newInputStream(SCHEMA_V1)) {
            return new EventContract(registry.getSchema(in));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Returns the violations of {@code event}; empty when the event honours the contract. */
    List<String> violations(JsonNode event) {
        return schema.validate(event).stream().map(Error::toString).toList();
    }
}
