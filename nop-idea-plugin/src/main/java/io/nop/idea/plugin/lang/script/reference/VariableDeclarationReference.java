package io.nop.idea.plugin.lang.script.reference;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReferenceBase;
import io.nop.idea.plugin.lang.script.psi.IdentifierNode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author <a href="mailto:flytreeleft@crazydan.org">flytreeleft</a>
 * @date 2025-07-04
 */
public class VariableDeclarationReference extends PsiReferenceBase<PsiElement> {
    private final IdentifierNode identifier;

    public VariableDeclarationReference(
            @NotNull PsiElement element, IdentifierNode identifier, TextRange rangeInElement
    ) {
        super(element, rangeInElement);
        this.identifier = identifier;
    }

    @Override
    public @Nullable PsiElement resolve() {
        return identifier;
    }

    @Override
    public Object @NotNull [] getVariants() {
        return super.getVariants();
    }
}
