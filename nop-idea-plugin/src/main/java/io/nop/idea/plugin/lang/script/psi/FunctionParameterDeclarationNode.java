package io.nop.idea.plugin.lang.script.psi;

import com.intellij.lang.ASTNode;
import org.jetbrains.annotations.NotNull;

/**
 * 函数参数定义节点
 *
 * @author <a href="mailto:flytreeleft@crazydan.org">flytreeleft</a>
 * @date 2025-06-30
 */
public class FunctionParameterDeclarationNode extends RuleSpecNode {

    public FunctionParameterDeclarationNode(@NotNull ASTNode node) {
        super(node);
    }
}
