package io.nop.idea.plugin.lang.script.psi;

import com.intellij.lang.ASTNode;
import org.jetbrains.annotations.NotNull;

/**
 * 对象的属性赋值节点
 * <p/>
 * 如 <code>{a, b: 1}</code> 中，<code>a</code> 和 <code>b: 1</code> 均为该类型节点
 *
 * @author <a href="mailto:flytreeleft@crazydan.org">flytreeleft</a>
 * @date 2025-06-30
 */
public class ObjectPropertyAssignmentNode extends RuleSpecNode {

    public ObjectPropertyAssignmentNode(@NotNull ASTNode node) {
        super(node);
    }
}
