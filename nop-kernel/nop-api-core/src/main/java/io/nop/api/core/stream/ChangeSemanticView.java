package io.nop.api.core.stream;

/**
 * Optional view implemented by record values that carry CDC change semantics (WI7,
 * plan 2336). The stream engine reads this view at the source emission points and
 * stamps the built {@code StreamRecord} with engine-level change metadata
 * ({@link RowKind} + table id), so the semantics become visible to the engine and its
 * sinks without any connector-specific coupling.
 *
 * <p>Placed in nop-api-core because both sides of the bridge (the stream engine and
 * value types such as the Debezium {@code ChangeEvent}) already depend on api-core;
 * a stream-core interface would invert the message-layer dependency direction.
 */
public interface ChangeSemanticView {

    /**
     * The change kind of this value (never null).
     */
    RowKind getRowKind();

    /**
     * The identifier of the table this change belongs to, or null when the value does
     * not carry a table identity.
     */
    String getTableId();
}
