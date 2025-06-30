package io.nop.idea.plugin.lang.script.psi;

import com.intellij.lang.ASTNode;
import org.jetbrains.annotations.NotNull;

/**
 * 表达式中的元素节点
 * <p/>
 * 其叶子节点可以为引用的变量名（identifier 类型），也可以为字面量（literal 类型）。
 * 其可以多层嵌套：
 * <pre>
 * 表达式 a.b.c(1, 2, 3) 除自身外，还包含以下元素
 * - a
 * - a.b
 * - a.b.c
 * - 1
 * - 2
 * - 3
 * </pre>
 *
 * @author <a href="mailto:flytreeleft@crazydan.org">flytreeleft</a>
 * @date 2025-06-30
 */
public class ExpressionElementNode extends RuleSpecNode {

    public ExpressionElementNode(@NotNull ASTNode node) {
        super(node);
    }
}
