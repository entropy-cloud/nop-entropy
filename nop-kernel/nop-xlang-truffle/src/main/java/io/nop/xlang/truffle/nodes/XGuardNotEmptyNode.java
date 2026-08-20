package io.nop.xlang.truffle.nodes;

import com.oracle.truffle.api.frame.VirtualFrame;
import io.nop.api.core.util.SourceLocation;
import io.nop.xlang.exec.XLangSemantics;

/**
 * 非空守卫节点（GuardNotEmptyExecutable 直译）：值单次求值，经共享 helper 校验非空
 * （空值抛 ERR_EXEC_VALUE_NOT_ALLOW_EMPTY），返回原值。
 */
public final class XGuardNotEmptyNode extends XExprNode {

    private final SourceLocation loc;

    private final String display;

    private final String target;

    private final XExprNode value;

    public XGuardNotEmptyNode(SourceLocation loc, String display, String target, XExprNode value) {
        this.loc = loc;
        this.display = display;
        this.target = target;
        this.value = value;
    }

    @Override
    public Object execute(VirtualFrame frame) {
        return XLangSemantics.guardNotEmpty(loc, display, target, value.execute(frame));
    }
}
