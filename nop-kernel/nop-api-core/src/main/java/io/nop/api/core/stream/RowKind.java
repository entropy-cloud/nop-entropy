package io.nop.api.core.stream;

/**
 * The change-kind of a stream record in the changelog (CDC) sense, mirroring the four
 * Flink RowKind values. Engine-level OPTIONAL record metadata (WI7, plan 2336): a
 * record without an explicit kind is INSERT-only append semantics.
 */
public enum RowKind {
    INSERT,
    UPDATE_BEFORE,
    UPDATE_AFTER,
    DELETE;

    /**
     * Whether records of this kind are plain appends (the default engine contract:
     * every non-INSERT kind requires a change-aware sink).
     */
    public boolean isInsertOnly() {
        return this == INSERT;
    }
}
