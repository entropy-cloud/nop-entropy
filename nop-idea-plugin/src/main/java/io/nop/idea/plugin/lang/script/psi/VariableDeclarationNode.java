package io.nop.idea.plugin.lang.script.psi;

import com.intellij.lang.ASTNode;
import org.jetbrains.annotations.NotNull;

/**
 * 含 <code>const</code> 和 <code>let</code> 语句
 *
 * @author <a href="mailto:flytreeleft@crazydan.org">flytreeleft</a>
 * @date 2025-06-30
 */
public class VariableDeclarationNode extends RuleSpecNode {

    public VariableDeclarationNode(@NotNull ASTNode node) {
        super(node);
    }
}
