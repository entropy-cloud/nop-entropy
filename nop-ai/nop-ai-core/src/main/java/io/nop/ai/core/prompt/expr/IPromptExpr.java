package io.nop.ai.core.prompt.expr;

import io.nop.api.core.util.ISourceLocationGetter;
import io.nop.core.lang.eval.IEvalScope;

public interface IPromptExpr extends ISourceLocationGetter{
    String toString();

    void renderTo(StringBuilder sb, IEvalScope scope);

    default String render(IEvalScope scope){
        StringBuilder sb = new StringBuilder();
        renderTo(sb, scope);
        return sb.toString();
    }
}
