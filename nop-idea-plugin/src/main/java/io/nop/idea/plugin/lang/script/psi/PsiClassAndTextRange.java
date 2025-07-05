package io.nop.idea.plugin.lang.script.psi;

import java.util.Collection;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import io.nop.idea.plugin.lang.script.reference.PsiClassReference;
import org.jetbrains.annotations.NotNull;

/**
 * @author <a href="mailto:flytreeleft@crazydan.org">flytreeleft</a>
 * @date 2025-07-05
 */
public record PsiClassAndTextRange(PsiClass clazz, TextRange textRange) {

    public static PsiReference @NotNull [] createReferences(PsiElement element, Collection<PsiClassAndTextRange> list) {
        return list.stream()
                   .filter(e -> e.clazz != null)
                   .map(e -> createReference(element, e))
                   .toArray(PsiReference[]::new);
    }

    public static @NotNull PsiReference createReference(PsiElement element, PsiClassAndTextRange cat) {
        return new PsiClassReference(element, cat.clazz, cat.textRange);
    }
}
