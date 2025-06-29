package io.nop.idea.plugin.lang.script.psi;

import com.intellij.extapi.psi.ASTWrapperPsiElement;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import org.antlr.intellij.adaptor.psi.Trees;
import org.jetbrains.annotations.NotNull;

/**
 * @author <a href="mailto:flytreeleft@crazydan.org">flytreeleft</a>
 * @date 2025-06-29
 */
public class RuleSpecNode extends ASTWrapperPsiElement {

    public RuleSpecNode(@NotNull ASTNode node) {
        super(node);
    }

    @Override
    public PsiElement @NotNull [] getChildren() {return Trees.getChildren(this);}
}
