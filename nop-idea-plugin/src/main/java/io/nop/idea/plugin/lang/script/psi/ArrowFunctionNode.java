package io.nop.idea.plugin.lang.script.psi;

import com.intellij.lang.ASTNode;
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
}
