package io.nop.xlang.compare;

import io.nop.api.core.util.SourceLocation;

import java.util.Objects;

/**
 * 输出缓冲（IEvalOutput）API 调用序列中的一条录制记录。
 * 覆盖裸 StringBuilderEvalOutput 会丢失的事件信息（comment() 为 no-op、loc/value 分事件）。
 */
public final class RecordedOutputCall {
    public enum Op {
        COMMENT, VALUE, TEXT
    }

    private final Op op;
    private final SourceLocation loc;
    private final Object value;

    public RecordedOutputCall(Op op, SourceLocation loc, Object value) {
        this.op = Objects.requireNonNull(op, "op");
        this.loc = loc;
        this.value = value;
    }

    public Op getOp() {
        return op;
    }

    public SourceLocation getLoc() {
        return loc;
    }

    public Object getValue() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (!(o instanceof RecordedOutputCall))
            return false;
        RecordedOutputCall that = (RecordedOutputCall) o;
        return op == that.op && Objects.equals(loc, that.loc) && CompareValues.typedEquals(value, that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(op, loc, value == null ? null : value.getClass(), String.valueOf(value));
    }

    @Override
    public String toString() {
        return op + "@" + loc + "(" + value + ")";
    }
}
