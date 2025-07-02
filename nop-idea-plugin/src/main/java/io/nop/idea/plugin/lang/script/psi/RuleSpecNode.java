package io.nop.idea.plugin.lang.script.psi;

import com.intellij.extapi.psi.ASTWrapperPsiElement;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.application.ReadAction;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
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
    public PsiElement @NotNull [] getChildren() {
        return Trees.getChildren(this);
    }

    @Override
    public PsiReference @NotNull [] getReferences() {
        // 在没有写入动作时，才执行函数并返回结果，从而避免阻塞编辑操作
        return ReadAction.compute(this::doGetReferences);
    }

    protected PsiReference @NotNull [] doGetReferences() {
        return PsiReference.EMPTY_ARRAY;
    }
}
