package io.nop.idea.plugin.lang.script.psi;

import com.intellij.lang.ASTNode;
import org.jetbrains.annotations.NotNull;

/**
 * @author <a href="mailto:flytreeleft@crazydan.org">flytreeleft</a>
 * @date 2025-06-29
 */
public class ImportQualifiedNameNode extends RuleSpecNode {

    public ImportQualifiedNameNode(@NotNull ASTNode node) {
        super(node);
    }
}
