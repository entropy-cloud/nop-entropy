package io.nop.idea.plugin.lang.script.psi;

import java.util.Map;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiClass;
import org.jetbrains.annotations.NotNull;

/**
 * {@link Identifier} 节点
 *
 * @author <a href="mailto:flytreeleft@crazydan.org">flytreeleft</a>
 * @date 2025-07-02
 */
public class IdentifierNode extends RuleSpecNode {

    public IdentifierNode(@NotNull ASTNode node) {
        super(node);
    }

    /** 获取变量的数据类型 */
    public PsiClass getDataType() {
        String varName = getText();

        Map<String, VarDecl> vars = getVisibleVarTypes();
        VarDecl varDecl = vars.get(varName);

        return varDecl != null ? varDecl.type : null;
    }
}
