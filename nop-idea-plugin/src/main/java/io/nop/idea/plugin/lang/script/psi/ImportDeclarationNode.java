package io.nop.idea.plugin.lang.script.psi;

import com.intellij.lang.ASTNode;
import org.jetbrains.annotations.NotNull;

/**
 * <code>import xxx;</code> 节点（含结束符）
 *
 * @author <a href="mailto:flytreeleft@crazydan.org">flytreeleft</a>
 * @date 2025-06-29
 */
public class ImportDeclarationNode extends RuleSpecNode {

    public ImportDeclarationNode(@NotNull ASTNode node) {
        super(node);
    }
}
