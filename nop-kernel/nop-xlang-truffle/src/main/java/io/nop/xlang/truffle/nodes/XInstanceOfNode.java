package io.nop.xlang.truffle.nodes;

import com.oracle.truffle.api.frame.VirtualFrame;
import io.nop.xlang.exec.XLangSemantics;

/**
 * instanceof 判定节点（InstanceOfExecutable 直译）：翻译期固化目标类名，运行时经共享
 * helper 判定（null 恒 false）。
 */
public final class XInstanceOfNode extends XExprNode {

    private final String className;

    private final XExprNode value;

    public XInstanceOfNode(String className, XExprNode value) {
        this.className = className;
        this.value = value;
    }

    @Override
    public Object execute(VirtualFrame frame) {
        return XLangSemantics.instanceOf(value.execute(frame), className);
    }
}
