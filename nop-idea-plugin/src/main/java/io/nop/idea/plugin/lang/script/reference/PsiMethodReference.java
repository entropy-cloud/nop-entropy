package io.nop.idea.plugin.lang.script.reference;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiReferenceBase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 对类方法的引用
 *
 * @author <a href="mailto:flytreeleft@crazydan.org">flytreeleft</a>
 * @date 2025-07-01
 */
public class PsiMethodReference extends PsiReferenceBase<PsiElement> {
    private final PsiMethod method;

    public PsiMethodReference(@NotNull PsiElement element, PsiMethod method, TextRange rangeInElement) {
        super(element, rangeInElement);
        this.method = method;
    }

    @Override
    public @Nullable PsiElement resolve() {
        return method;
    }

    @Override
    public Object @NotNull [] getVariants() {
        return super.getVariants();
    }
}
