package io.nop.xlang.truffle.nodes;

import com.oracle.truffle.api.frame.VirtualFrame;

/**
 * break 节点（BreakExecutable 直译）：抛出 {@link XLBreakException}（ExitMode.BREAK 载体，
 * 三值一一对应之一）——由词法最近循环节点捕获消费；穿透视 catch（显式放行）与 switch
 * （不消费，live 语义）；函数/程序边界吞没（边界清零）。
 */
public final class XBreakNode extends XExprNode {

    public static final XBreakNode INSTANCE = new XBreakNode();

    private XBreakNode() {
    }

    @Override
    public Object execute(VirtualFrame frame) {
        throw XLBreakException.INSTANCE;
    }
}
