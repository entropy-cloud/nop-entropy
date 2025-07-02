package io.nop.idea.plugin.lang.script.psi;

import com.intellij.lang.ASTNode;
import org.jetbrains.annotations.NotNull;

/**
 * 对象成员（包含成员变量和方法）节点
 * <p/>
 * 如 <code>a.b.c()</code>、<code>{b, c: 1}</code> 中，
 * <code>b</code> 和 <code>c</code> 均为该类型节点
 *
 * @author <a href="mailto:flytreeleft@crazydan.org">flytreeleft</a>
 * @date 2025-06-30
 */
public class ObjectMemberNode extends RuleSpecNode {

    public ObjectMemberNode(@NotNull ASTNode node) {
        super(node);
    }
}
