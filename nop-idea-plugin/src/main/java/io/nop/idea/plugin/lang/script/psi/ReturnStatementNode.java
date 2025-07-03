package io.nop.idea.plugin.lang.script.psi;

import com.intellij.lang.ASTNode;
import org.jetbrains.annotations.NotNull;

/**
 * <code>return</code> 语句节点
 *
 * @author <a href="mailto:flytreeleft@crazydan.org">flytreeleft</a>
 * @date 2025-07-03
 */
public class ReturnStatementNode extends RuleSpecNode {

    public ReturnStatementNode(@NotNull ASTNode node) {
        super(node);
    }
}
