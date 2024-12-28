package io.nop.ai.core.prompt;

import io.nop.ai.core.prompt._gen._PromptModel;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.xlang.api.XLang;

import java.util.Map;

public class PromptModel extends _PromptModel implements IPromptTemplate {

    public PromptModel() {

    }

    @Override
    public String generatePrompt(Map<String, Object> vars) {
        IEvalScope scope = XLang.newEvalScope(vars);
        return getTemplate().generateText(scope);
    }
}
