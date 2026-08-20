package io.nop.xlang.compare;

import io.nop.api.core.util.SourceLocation;

import java.util.Map;
import java.util.Objects;

/**
 * 输出缓冲（IEvalOutput）API 调用序列中的一条录制记录。
 * 覆盖裸 StringBuilderEvalOutput 会丢失的事件信息（comment() 为 no-op、loc/value 分事件）。
 *
 * <p>节点事件（I4 §8 裁定：{@code RecordingEvalOutput} 扩展实现 {@code IXNodeHandler} 后的
 * beginNode/endNode/simpleNode 录制，GenNode 族通路对两列对称可达）：value = tagName，
 * attrs = 属性名 → 属性值的平值映射（剥离 ValueWithLocation 定位信息，等价性按值判定）。
 */
public final class RecordedOutputCall {
    public enum Op {
        COMMENT, VALUE, TEXT, BEGIN_DOC, END_DOC, BEGIN_NODE, END_NODE, SIMPLE_NODE
    }

    private final Op op;
    private final SourceLocation loc;
    private final Object value;
    private final Map<String, Object> attrs;

    public RecordedOutputCall(Op op, SourceLocation loc, Object value) {
        this(op, loc, value, null);
    }

    public RecordedOutputCall(Op op, SourceLocation loc, Object value, Map<String, Object> attrs) {
        this.op = Objects.requireNonNull(op, "op");
        this.loc = loc;
        this.value = value;
        this.attrs = attrs;
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

    /** 节点事件的属性平值映射（非节点事件为 null）。 */
    public Map<String, Object> getAttrs() {
        return attrs;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (!(o instanceof RecordedOutputCall))
            return false;
        RecordedOutputCall that = (RecordedOutputCall) o;
        return op == that.op && Objects.equals(loc, that.loc) && CompareValues.typedEquals(value, that.value)
                && Objects.equals(attrs, that.attrs);
    }

    @Override
    public int hashCode() {
        return Objects.hash(op, loc, value == null ? null : value.getClass(), String.valueOf(value), attrs);
    }

    @Override
    public String toString() {
        return attrs == null ? op + "@" + loc + "(" + value + ")"
                : op + "@" + loc + "(" + value + ", attrs=" + attrs + ")";
    }
}
