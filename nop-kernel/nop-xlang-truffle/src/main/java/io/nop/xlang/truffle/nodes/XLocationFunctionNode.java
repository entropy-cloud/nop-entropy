package io.nop.xlang.truffle.nodes;

import com.oracle.truffle.api.frame.VirtualFrame;
import io.nop.api.core.util.SourceLocation;

/**
 * 调用位置函数节点（LocationFunction 直译，I4 三边缘类裁定转译并入）：返回编译期固化的
 * 调用点 SourceLocation（与解释器 getLocation() 同一常量）。
 */
public final class XLocationFunctionNode extends XExprNode {

    private final SourceLocation loc;

    public XLocationFunctionNode(SourceLocation loc) {
        this.loc = loc;
    }

    @Override
    public Object execute(VirtualFrame frame) {
        return loc;
    }
}
