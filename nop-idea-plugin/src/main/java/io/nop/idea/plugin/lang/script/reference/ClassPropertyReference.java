package io.nop.idea.plugin.lang.script.reference;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReferenceBase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 对类属性的引用
 *
 * @author <a href="mailto:flytreeleft@crazydan.org">flytreeleft</a>
 * @date 2025-07-01
 */
public class ClassPropertyReference extends PsiReferenceBase<PsiElement> {

    public ClassPropertyReference(@NotNull PsiElement element, TextRange rangeInElement) {
        super(element, rangeInElement);
    }

    @Override
    public @Nullable PsiElement resolve() {
        return null;
    }

    @Override
    public Object @NotNull [] getVariants() {
        return super.getVariants();
    }
}
