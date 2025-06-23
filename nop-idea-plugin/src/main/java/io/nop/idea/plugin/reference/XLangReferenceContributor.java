package io.nop.idea.plugin.reference;

import com.intellij.openapi.fileTypes.FileType;
import com.intellij.patterns.ElementPattern;
import com.intellij.patterns.ObjectPattern;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.patterns.PsiFilePattern;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReferenceContributor;
import com.intellij.psi.PsiReferenceRegistrar;
import io.nop.idea.plugin.lang.XLangFileType;
import org.jetbrains.annotations.NotNull;

import static com.intellij.psi.PsiReferenceRegistrar.HIGHER_PRIORITY;

/**
 * 对 XLang 中的引用进行识别
 *
 * @author <a href="mailto:flytreeleft@crazydan.org">flytreeleft</a>
 * @date 2025-06-22
 */
public class XLangReferenceContributor extends PsiReferenceContributor {

    @Override
    public void registerReferenceProviders(@NotNull PsiReferenceRegistrar registrar) {
        ElementPattern<? extends PsiElement> pattern = PlatformPatterns.psiElement()
                                                                       .inFile(withFileType(XLangFileType.class));

        // Note: 对 XLang 文件的引用解析采用最高优先级，确保在 PsiMultiReference 中将解析到的 XLang 引用作为优先引用
        registrar.registerReferenceProvider(pattern, new XLangReferenceProvider(), HIGHER_PRIORITY);
    }

    static <T extends FileType> PsiFilePattern<?, ?> withFileType(Class<T> type) {
        return PlatformPatterns.psiFile().withFileType(new FileTypePattern<>(type));
    }

    static class FileTypePattern<T extends FileType> extends ObjectPattern<T, FileTypePattern<T>> {

        protected FileTypePattern(@NotNull Class<T> aClass) {
            super(aClass);
        }
    }
}
