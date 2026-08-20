package io.nop.xlang.truffle.nodes;

import com.oracle.truffle.api.frame.VirtualFrame;
import io.nop.core.model.query.FilterOp;

/**
 * 断言谓词节点（AssertOpExecutable 直译）：值单次求值，经 FilterOp 统一谓词判定。
 */
public final class XAssertOpNode extends XExprNode {

    private final FilterOp filterOp;

    private final XExprNode value;

    public XAssertOpNode(FilterOp filterOp, XExprNode value) {
        this.filterOp = filterOp;
        this.value = value;
    }

    @Override
    public Object execute(VirtualFrame frame) {
        return filterOp.getPredicate().test(value.execute(frame));
    }
}
