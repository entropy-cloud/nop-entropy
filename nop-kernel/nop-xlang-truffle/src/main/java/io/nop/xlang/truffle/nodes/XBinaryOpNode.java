package io.nop.xlang.truffle.nodes;

import com.oracle.truffle.api.frame.VirtualFrame;
import io.nop.xlang.exec.XLangSemantics;

/**
 * 二元运算节点（算术/比较族直译）：语义敏感操作统一走共享 helper（D3 口径，与解释器/
 * java 生成代码同一实现来源）。Strict 变体与宽松变体 live 同实现（xlangEq），翻译层合一。
 */
public final class XBinaryOpNode extends XExprNode {

    public enum Op {
        PLUS {
            @Override
            Object apply(Object v1, Object v2) {
                return XLangSemantics.plus(v1, v2);
            }
        },
        MINUS {
            @Override
            Object apply(Object v1, Object v2) {
                return XLangSemantics.minus(v1, v2);
            }
        },
        MULTIPLY {
            @Override
            Object apply(Object v1, Object v2) {
                return XLangSemantics.multiply(v1, v2);
            }
        },
        DIVIDE {
            @Override
            Object apply(Object v1, Object v2) {
                return XLangSemantics.divide(v1, v2);
            }
        },
        EQ {
            @Override
            Object apply(Object v1, Object v2) {
                return XLangSemantics.eq(v1, v2);
            }
        },
        NE {
            @Override
            Object apply(Object v1, Object v2) {
                return XLangSemantics.ne(v1, v2);
            }
        },
        GT {
            @Override
            Object apply(Object v1, Object v2) {
                return XLangSemantics.gt(v1, v2);
            }
        },
        GE {
            @Override
            Object apply(Object v1, Object v2) {
                return XLangSemantics.ge(v1, v2);
            }
        },
        LT {
            @Override
            Object apply(Object v1, Object v2) {
                return XLangSemantics.lt(v1, v2);
            }
        },
        LE {
            @Override
            Object apply(Object v1, Object v2) {
                return XLangSemantics.le(v1, v2);
            }
        };

        abstract Object apply(Object v1, Object v2);
    }

    private final Op op;

    private final XExprNode left;

    private final XExprNode right;

    public XBinaryOpNode(Op op, XExprNode left, XExprNode right) {
        this.op = op;
        this.left = left;
        this.right = right;
    }

    @Override
    public Object execute(VirtualFrame frame) {
        return op.apply(left.execute(frame), right.execute(frame));
    }
}
