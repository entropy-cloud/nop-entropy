package io.nop.ai.core.prompt;

import io.nop.ai.core.api.chat.AiChatOptions;
import io.nop.ai.core.api.messages.AiChatExchange;
import io.nop.ai.core.model.PromptInputModel;
import io.nop.ai.core.model.PromptOutputModel;
import io.nop.api.core.util.Guard;
import io.nop.commons.util.StringHelper;
import io.nop.core.context.IEvalContext;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.type.PredefinedGenericTypes;
import io.nop.xlang.api.XLang;

import java.util.List;
import java.util.Map;

public class SimplePromptTemplate implements IPromptTemplate {
    private final String name;
    private final String content;
    private final PromptOutputModel result;

    public SimplePromptTemplate(String name, String content) {
        this.name = Guard.notEmpty(name, "name");
        this.content = Guard.notEmpty(content, "content");
        this.result = new PromptOutputModel();
        result.setName("RESULT");
        result.setType(PredefinedGenericTypes.STRING_TYPE);
    }

    public static SimplePromptTemplate simplePrompt(String name, String content) {
        return new SimplePromptTemplate(name, content);
    }

    @Override
    public String getDisplayName() {
        return name;
    }

    @Override
    public String getDescription() {
        return "";
    }

    @Override
    public List<PromptInputModel> getInputs() {
        return List.of();
    }

    @Override
    public PromptInputModel getInput(String name) {
        return null;
    }

    @Override
    public List<PromptOutputModel> getOutputs() {
        return List.of(result);
    }

    @Override
    public PromptOutputModel getOutput(String name) {
        if ("RESULT".equals(name))
            return result;
        return null;
    }

    @Override
    public void applyChatOptions(AiChatOptions chatOptions) {
    }

    @Override
    public IEvalScope prepareInputs(Map<String, Object> vars, IEvalContext ctx) {
        if (ctx == null)
            return XLang.newEvalScope(vars);
        return ctx.getEvalScope().newChildScope(vars);
    }

    @Override
    public String generatePrompt(IEvalScope scope) {
        return StringHelper.renderTemplate(content, scope::getValueByPropPath);
    }

    @Override
    public void processChatResponse(AiChatExchange chatResponse, IEvalScope scope) {

    }

    @Override
    public String getName() {
        return name;
    }
}
