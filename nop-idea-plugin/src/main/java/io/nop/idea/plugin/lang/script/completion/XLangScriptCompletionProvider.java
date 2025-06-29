package io.nop.idea.plugin.lang.script.completion;

import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionProvider;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;

/**
 * @author <a href="mailto:flytreeleft@crazydan.org">flytreeleft</a>
 * @date 2025-06-29
 */
public class XLangScriptCompletionProvider extends CompletionProvider<CompletionParameters> {

    @Override
    protected void addCompletions(
            @NotNull CompletionParameters parameters, @NotNull ProcessingContext context,
            @NotNull CompletionResultSet result
    ) {
    }
}
