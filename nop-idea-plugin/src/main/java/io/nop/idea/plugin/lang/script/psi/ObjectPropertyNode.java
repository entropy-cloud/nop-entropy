package io.nop.idea.plugin.lang.script.psi;

import com.intellij.lang.ASTNode;
import org.jetbrains.annotations.NotNull;

/**
 * 对象的属性节点
 * <p/>
 * 如 <code>a.b.c()</code>、<code>{b, c: 1}</code> 中，
 * <code>b</code> 和 <code>c</code> 均为该类型节点
 *
 * @author <a href="mailto:flytreeleft@crazydan.org">flytreeleft</a>
 * @date 2025-06-30
 */
public class ObjectPropertyNode extends RuleSpecNode {

    public ObjectPropertyNode(@NotNull ASTNode node) {
        super(node);
    }
}
