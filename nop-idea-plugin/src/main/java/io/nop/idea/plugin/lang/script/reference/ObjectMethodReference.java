package io.nop.idea.plugin.lang.script.reference;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.util.IncorrectOperationException;
import io.nop.idea.plugin.lang.reference.XLangReferenceBase;
import io.nop.idea.plugin.lang.script.psi.ExpressionNode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 对象方法引用
 *
 * @author <a href="mailto:flytreeleft@crazydan.org">flytreeleft</a>
 * @date 2025-07-06
 */
public class ObjectMethodReference extends XLangReferenceBase {

    public ObjectMethodReference(ExpressionNode myElement) {
        super(myElement, null);
    }

    @Override
    protected TextRange calculateDefaultRangeInElement() {
        return ((ExpressionNode) myElement).getObjectMemberTextRange();
    }

    @Override
    public @Nullable PsiElement resolveInner() {
        return ((ExpressionNode) myElement).getObjectMethod();
    }

    @Override
    public PsiElement handleElementRename(@NotNull String newElementName) throws IncorrectOperationException {
        return null;
    }

    @Override
    public PsiElement bindToElement(@NotNull PsiElement element) throws IncorrectOperationException {
        return null;
    }
}
