package io.nop.xlang.truffle.nodes;

import com.oracle.truffle.api.frame.VirtualFrame;

/**
 * 序列节点（SeqExecutable/BlockExecutable 直译）：顺序执行子节点；非 block 序列返回末值，
 * block 语句序列执行全部子节点后返回 null。子集内不存在 ExitMode 控制流节点
 * （rt.getExitMode() 恒为 null，解释器的 exitMode 短路分支在本子集不可达）。
 */
public final class XSeqNode extends XExprNode {

    private final XExprNode[] exprs;

    private final boolean blockStatement;

    public XSeqNode(XExprNode[] exprs, boolean blockStatement) {
        this.exprs = exprs;
        this.blockStatement = blockStatement;
    }

    @Override
    public Object execute(VirtualFrame frame) {
        Object ret = null;
        for (XExprNode expr : exprs) {
            ret = expr.execute(frame);
        }
        return blockStatement ? null : ret;
    }
}
