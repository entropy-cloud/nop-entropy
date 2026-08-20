package io.nop.xlang.truffle.nodes;

import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.frame.VirtualFrame;
import io.nop.xlang.exec.ExecutableFunction;

/**
 * 函数字面量载荷下降节点（LiteralExecutable(ExecutableFunction) 直译，plan I7 与 java 侧
 * I4 Phase 1 §5 载荷处置对称的 truffle 形态）：函数体翻译为独立 RootNode（own
 * FrameDescriptor），求值返回 {@link XLangTruffleFunction}（无捕获形态 captured = 空快照，
 * 与解释器返回 ExecutableFunction 函数对象语义一致——值经函数值调用点分派执行）。
 */
public final class XFunctionValueNode extends XExprNode {

    private final RootCallTarget target;

    public XFunctionValueNode(ExecutableFunction func, RootCallTarget target) {
        this.target = target;
    }

    @Override
    public Object execute(VirtualFrame frame) {
        return new XLangTruffleFunction(target, new Object[0]);
    }
}
