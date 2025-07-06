package io.nop.idea.plugin.lang.script.reference;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.util.IncorrectOperationException;
import io.nop.idea.plugin.lang.XLangVarDecl;
import io.nop.idea.plugin.lang.reference.XLangReferenceBase;
import io.nop.idea.plugin.lang.script.psi.IdentifierNode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * {@link IdentifierNode} 的引用
 * <p/>
 * 涉及对变量和类名的引用
 *
 * @author <a href="mailto:flytreeleft@crazydan.org">flytreeleft</a>
 * @date 2025-07-06
 */
public class IdentifierReference extends XLangReferenceBase {
    private final IdentifierNode identifier;

    public IdentifierReference(PsiElement myElement, IdentifierNode identifier, TextRange myRangeInElement) {
        super(myElement, myRangeInElement);
        this.identifier = identifier;
    }

    @Override
    public @Nullable PsiElement resolveInner() {
        if (!identifier.isValid()) {
            return null;
        }

        XLangVarDecl varDecl = identifier.getVarDecl();

        return varDecl != null ? varDecl.element() : null;
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
