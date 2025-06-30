package io.nop.idea.plugin.lang.script.psi;

import com.intellij.lang.ASTNode;
import org.jetbrains.annotations.NotNull;

/**
 * 各种语句的根节点
 * <p/>
 * 赋值、函数、导入、try 等语句，各自均有唯一的根节点
 *
 * @author <a href="mailto:flytreeleft@crazydan.org">flytreeleft</a>
 * @date 2025-06-30
 */
public class StatementRootNode extends RuleSpecNode {

    public StatementRootNode(@NotNull ASTNode node) {
        super(node);
    }
}
