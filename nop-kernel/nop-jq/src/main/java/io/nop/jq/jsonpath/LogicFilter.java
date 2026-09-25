package io.nop.jq.jsonpath;

import io.nop.jq.JsonAccessor;

/**
 * Logic filter that combines two filters with AND or OR.
 */
public class LogicFilter implements Filter {
    public enum LogicOp {
        AND, OR
    }

    private final Filter left;
    private final Filter right;
    private final LogicOp op;

    public LogicFilter(Filter left, LogicOp op, Filter right) {
        this.left = left;
        this.op = op;
        this.right = right;
    }

    @Override
    public boolean apply(JsonAccessor accessor, Object root, Object item) {
        return switch (op) {
            case AND -> left.apply(accessor, root, item) && right.apply(accessor, root, item);
            case OR -> left.apply(accessor, root, item) || right.apply(accessor, root, item);
        };
    }

    @Override
    public String toString() {
        return "(" + left + " " + op + " " + right + ")";
    }
}
