package io.nop.idea.plugin.lang.script.psi;

import com.intellij.lang.ASTNode;
import org.jetbrains.annotations.NotNull;

/**
 * 对象声明节点，如：
 * <pre>
 * {a, b: 1}
 * </pre>
 *
 * @author <a href="mailto:flytreeleft@crazydan.org">flytreeleft</a>
 * @date 2025-07-02
 */
public class ObjectDeclarationNode extends RuleSpecNode {

    public ObjectDeclarationNode(@NotNull ASTNode node) {
        super(node);
    }
}
