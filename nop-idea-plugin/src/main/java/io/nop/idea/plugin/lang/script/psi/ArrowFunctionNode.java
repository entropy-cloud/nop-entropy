package io.nop.idea.plugin.lang.script.psi;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiClass;
import org.jetbrains.annotations.NotNull;

/**
 * 箭头函数节点
 *
 * @author <a href="mailto:flytreeleft@crazydan.org">flytreeleft</a>
 * @date 2025-06-30
 */
public class ArrowFunctionNode extends RuleSpecNode {

    public ArrowFunctionNode(@NotNull ASTNode node) {
        super(node);
    }

    /** 获取函数的返回值类型 */
    public PsiClass getReturnType() {
        // TODO 分析 return 表达式，得到返回类型
        return null;
    }
}
