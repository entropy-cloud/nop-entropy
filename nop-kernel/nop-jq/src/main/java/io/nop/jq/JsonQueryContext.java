package io.nop.jq;

/**
 * Context object that carries configuration and state during JsonPath/jq query execution.
 * Passed through the Segment pipeline and Filter evaluation.
 */
public class JsonQueryContext {
    private final JsonAccessor accessor;
    private final boolean suppressException;

    public JsonQueryContext(JsonAccessor accessor, boolean suppressException) {
        this.accessor = accessor;
        this.suppressException = suppressException;
    }

    public JsonQueryContext(JsonAccessor accessor) {
        this(accessor, false);
    }

    public static JsonQueryContext create() {
        return new JsonQueryContext(NopJsonAccessor.INSTANCE);
    }

    public JsonAccessor getAccessor() {
        return accessor;
    }

    public boolean isSuppressException() {
        return suppressException;
    }

    public void setSuppressException(boolean suppressException) {
        // Immutable for now; context is created once per query compilation
    }
}
