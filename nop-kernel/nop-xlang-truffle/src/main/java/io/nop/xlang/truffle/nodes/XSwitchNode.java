package io.nop.xlang.truffle.nodes;

import com.oracle.truffle.api.frame.VirtualFrame;

import java.util.Objects;

/**
 * switch 节点（SwitchExecutable 直译）：discriminant 与各 case test 依序 Objects.equals
 * 匹配（全部求值直至命中，与解释器一致）；命中后执行 consequence，无 fallthrough 即返回
 * （asExpr 返回分支值，语句形态返回 null）；default 兜底。<b>不消费控制流异常</b>
 * （live 语义：switch 不检查 exitMode——case 内 break 穿透至词法最近循环，与解释器一致）。
 */
public final class XSwitchNode extends XExprNode {

    private final boolean asExpr;

    private final XExprNode discriminant;

    private final XExprNode[] tests;

    private final XExprNode[] consequences;

    private final boolean[] fallthroughs;

    private final XExprNode defaultCase;

    public XSwitchNode(boolean asExpr, XExprNode discriminant, XExprNode[] tests,
                       XExprNode[] consequences, boolean[] fallthroughs, XExprNode defaultCase) {
        this.asExpr = asExpr;
        this.discriminant = discriminant;
        this.tests = tests;
        this.consequences = consequences;
        this.fallthroughs = fallthroughs;
        this.defaultCase = defaultCase;
    }

    @Override
    public Object execute(VirtualFrame frame) {
        Object value = discriminant.execute(frame);
        Object ret = null;
        for (int i = 0; i < tests.length; i++) {
            Object testValue = tests[i].execute(frame);
            if (Objects.equals(value, testValue)) {
                ret = consequences[i].execute(frame);
                if (!fallthroughs[i]) {
                    return asExpr ? ret : null;
                }
            }
        }
        if (defaultCase != null) {
            ret = defaultCase.execute(frame);
        }
        return asExpr ? ret : null;
    }
}
