package io.nop.idea.plugin.lang.script.psi;

import java.util.Map;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

/**
 * 各种语句的根节点
 * <p/>
 * 赋值、函数、导入、try 等语句，各自均有唯一的根节点
 *
 * @author <a href="mailto:flytreeleft@crazydan.org">flytreeleft</a>
 * @date 2025-06-30
 */
public class TopLevelStatementNode extends RuleSpecNode {

    public TopLevelStatementNode(@NotNull ASTNode node) {
        super(node);
    }

    @Override
    public @NotNull Map<String, VarDecl> getVarTypes() {
        PsiElement firstChild = getFirstChild();

        if (firstChild instanceof StatementNode s) {
            return s.getVarTypes();
        } //
        else if (firstChild.getFirstChild() instanceof ImportDeclarationNode i) {
            return i.getVarTypes();
        }

        return Map.of();
    }
}
