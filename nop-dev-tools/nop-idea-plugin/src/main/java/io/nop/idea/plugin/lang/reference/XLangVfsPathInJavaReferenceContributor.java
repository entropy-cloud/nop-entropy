/*
 * Copyright (c) 2017-2025 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */

package io.nop.idea.plugin.lang.reference;

import com.intellij.openapi.util.TextRange;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiReferenceContributor;
import com.intellij.psi.PsiReferenceProvider;
import com.intellij.psi.PsiReferenceRegistrar;
import com.intellij.util.ProcessingContext;
import io.nop.commons.util.StringHelper;
import io.nop.idea.plugin.vfs.NopVirtualFileReference;
import org.jetbrains.annotations.NotNull;

/**
 *
 * @author <a href="mailto:flytreeleft@crazydan.org">flytreeleft</a>
 * @date 2025-12-19
 */
public class XLangVfsPathInJavaReferenceContributor extends PsiReferenceContributor {

    @Override
    public void registerReferenceProviders(@NotNull PsiReferenceRegistrar registrar) {
        // 仅针对 Java 字符串字面量
        registrar.registerReferenceProvider(PlatformPatterns.psiElement(PsiLiteralExpression.class),
                                            new VfsPathReferenceProvider());
    }

    static class VfsPathReferenceProvider extends PsiReferenceProvider {
        @Override
        public PsiReference @NotNull [] getReferencesByElement(
                @NotNull PsiElement element,
                @NotNull ProcessingContext context
        ) {
            Object value = ((PsiLiteralExpression) element).getValue();

            if (value instanceof String text //
                && text.startsWith("/") //
                && StringHelper.isValidVPath(text) //
            ) {
                // Note: 有效范围需排除引号
                TextRange textRange = TextRange.create(0, text.length()).shiftRight(1);
                NopVirtualFileReference ref = new NopVirtualFileReference(element, textRange, text);

                if (ref.resolve() != null) {
                    return new PsiReference[] { ref };
                }
            }
            return PsiReference.EMPTY_ARRAY;
        }
    }
}
