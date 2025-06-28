package io.nop.idea.plugin.lang.script;

import com.intellij.codeInsight.completion.CompletionContributor;
import com.intellij.codeInsight.completion.CompletionInitializationContext;
import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.completion.JavaCompletionContributor;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.util.NlsContexts;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author <a href="mailto:flytreeleft@crazydan.org">flytreeleft</a>
 * @date 2025-06-28
 */
public class XLangScriptCompletionContributor extends CompletionContributor implements DumbAware {
    private static final JavaCompletionContributor java = new JavaCompletionContributor();

    @Override
    public void beforeCompletion(@NotNull CompletionInitializationContext context) {
        java.beforeCompletion(context);
    }

    @Override
    public void fillCompletionVariants(@NotNull CompletionParameters parameters, @NotNull CompletionResultSet result) {
        java.fillCompletionVariants(parameters, result);
    }

    @Override
    public @Nullable @NlsContexts.HintText String handleEmptyLookup(
            @NotNull CompletionParameters parameters, Editor editor
    ) {
        return java.handleEmptyLookup(parameters, editor);
    }
}
