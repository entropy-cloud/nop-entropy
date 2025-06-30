package io.nop.idea.plugin.lang.script.psi;

import com.intellij.lang.ASTNode;
import org.jetbrains.annotations.NotNull;

/**
 * <code>new</code> 语句中的类名节点
 *
 * @author <a href="mailto:flytreeleft@crazydan.org">flytreeleft</a>
 * @date 2025-06-30
 */
public class ParameterizedTypeNode extends RuleSpecNode {

    public ParameterizedTypeNode(@NotNull ASTNode node) {
        super(node);
    }
}
