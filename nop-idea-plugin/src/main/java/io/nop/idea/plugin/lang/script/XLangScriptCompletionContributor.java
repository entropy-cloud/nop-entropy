package io.nop.idea.plugin.lang.script;

import com.intellij.codeInsight.completion.CompletionContributor;
import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.openapi.project.DumbAware;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.PsiElement;
import io.nop.idea.plugin.lang.script.completion.XLangScriptCompletionProvider;

/**
 * @author <a href="mailto:flytreeleft@crazydan.org">flytreeleft</a>
 * @date 2025-06-28
 * @deprecated 通过实现 {@link PsiElement#getReferences()} 并返回 Java 相关的引用对象，便可实现自动补全，无需单独编写逻辑
 */
@Deprecated
public class XLangScriptCompletionContributor extends CompletionContributor implements DumbAware {

    public XLangScriptCompletionContributor() {
        extend(CompletionType.BASIC, PlatformPatterns.psiElement(), new XLangScriptCompletionProvider());
    }
}
