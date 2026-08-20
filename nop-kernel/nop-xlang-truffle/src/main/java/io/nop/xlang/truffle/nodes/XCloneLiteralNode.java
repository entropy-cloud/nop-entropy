package io.nop.xlang.truffle.nodes;

import com.oracle.truffle.api.frame.VirtualFrame;
import io.nop.api.core.util.CloneHelper;

/**
 * 可变字面量节点（CloneLiteralExecutable 直译）：每次求值经 {@link CloneHelper#deepClone}
 * 深拷贝（与解释器同一实现来源），防止可变字面量（List/Map）跨求值共享。
 */
public final class XCloneLiteralNode extends XExprNode {

    private final Object value;

    public XCloneLiteralNode(Object value) {
        this.value = value;
    }

    @Override
    public Object execute(VirtualFrame frame) {
        return CloneHelper.deepClone(value);
    }
}
