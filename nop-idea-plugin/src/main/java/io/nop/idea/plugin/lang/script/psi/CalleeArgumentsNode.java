package io.nop.idea.plugin.lang.script.psi;

import com.intellij.lang.ASTNode;
import org.jetbrains.annotations.NotNull;

/**
 * 函数调用的参数列表节点
 *
 * @author <a href="mailto:flytreeleft@crazydan.org">flytreeleft</a>
 * @date 2025-06-30
 */
public class CalleeArgumentsNode extends RuleSpecNode {

    public CalleeArgumentsNode(@NotNull ASTNode node) {
        super(node);
    }
}
