package io.nop.xlang.truffle.nodes;

import com.oracle.truffle.api.frame.VirtualFrame;

/**
 * continue 节点（ContinueExecutable 直译）：抛出 {@link XLContinueException}
 * （ExitMode.CONTINUE 载体）——由词法最近循环节点捕获消费；函数/程序边界吞没。
 *
 * <p>共享单例契约：跨编译单元复用，<b>不设 source section</b>（同 {@link XBreakNode}）。
 */
public final class XContinueNode extends XExprNode {

    public static final XContinueNode INSTANCE = new XContinueNode();

    private XContinueNode() {
    }

    @Override
    public Object execute(VirtualFrame frame) {
        throw XLContinueException.INSTANCE;
    }
}
