package io.nop.idea.plugin.lang.script;

import com.intellij.codeInsight.completion.CompletionContributor;
import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.openapi.project.DumbAware;
import com.intellij.patterns.PlatformPatterns;
import io.nop.idea.plugin.lang.script.completion.XLangScriptCompletionProvider;

/**
 * @author <a href="mailto:flytreeleft@crazydan.org">flytreeleft</a>
 * @date 2025-06-28
 */
public class XLangScriptCompletionContributor extends CompletionContributor implements DumbAware {

    public XLangScriptCompletionContributor() {
        extend(CompletionType.BASIC, PlatformPatterns.psiElement(), new XLangScriptCompletionProvider());
    }
}
