package io.nop.idea.plugin.lang.script.psi;

import com.intellij.lang.ASTNode;
import org.jetbrains.annotations.NotNull;

/**
 * 对象属性声明节点
 * <p/>
 * 如 <code>{a, b: 1}</code> 中的
 * <code>a</code>、<code>b: 1</code> 均为该类型节点
 *
 * @author <a href="mailto:flytreeleft@crazydan.org">flytreeleft</a>
 * @date 2025-07-02
 */
public class ObjectPropertyDeclarationNode extends RuleSpecNode {

    public ObjectPropertyDeclarationNode(@NotNull ASTNode node) {
        super(node);
    }
}
