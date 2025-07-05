package io.nop.idea.plugin.lang.script.reference;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReferenceBase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author <a href="mailto:flytreeleft@crazydan.org">flytreeleft</a>
 * @date 2025-07-04
 */
public class PsiClassReference extends PsiReferenceBase<PsiElement> {
    private final PsiClass clazz;

    public PsiClassReference(
            @NotNull PsiElement element, PsiClass clazz, TextRange rangeInElement
    ) {
        super(element, rangeInElement);
        this.clazz = clazz;
    }

    @Override
    public @Nullable PsiElement resolve() {
        return clazz;
    }

    @Override
    public Object @NotNull [] getVariants() {
        return super.getVariants();
    }
}
