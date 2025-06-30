package io.nop.idea.plugin.lang.script.psi;

import com.intellij.lang.ASTNode;
import org.jetbrains.annotations.NotNull;

/**
 * <code>{ ... }</code> 块节点
 * <p/>
 * 用于 <code>try</code>、<code>if</code> 和函数体等节点
 *
 * @author <a href="mailto:flytreeleft@crazydan.org">flytreeleft</a>
 * @date 2025-06-30
 */
public class BlockStatementNode extends RuleSpecNode {

    public BlockStatementNode(@NotNull ASTNode node) {
        super(node);
    }
}
