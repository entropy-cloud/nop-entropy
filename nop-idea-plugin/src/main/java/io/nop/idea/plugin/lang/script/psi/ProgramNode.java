package io.nop.idea.plugin.lang.script.psi;

import com.intellij.lang.ASTNode;
import org.jetbrains.annotations.NotNull;

/**
 * 注意，当 XScript 是嵌入在 &lt;c:script/> 标签中时，
 * {@link #getContext()} 或 {@link #getParent()}
 * 的结果为该标签节点，通过该节点向上可得到在 xlib 函数中定义的参数，
 * 从而可将其用于代码补全、引用跳转等
 *
 * @author <a href="mailto:flytreeleft@crazydan.org">flytreeleft</a>
 * @date 2025-06-29
 */
public class ProgramNode extends RuleSpecNode {

    public ProgramNode(@NotNull ASTNode node) {
        super(node);
    }

    // TODO 获取上下文环境中可访问的变量
}
