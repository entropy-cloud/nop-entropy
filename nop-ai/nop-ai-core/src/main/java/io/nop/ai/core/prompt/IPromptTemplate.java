package io.nop.ai.core.prompt;

import io.nop.ai.core.api.chat.AiChatOptions;
import io.nop.ai.core.api.messages.AiChatExchange;
import io.nop.ai.core.model.PromptInputModel;
import io.nop.ai.core.model.PromptOutputModel;
import io.nop.core.context.IEvalContext;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.xlang.xdsl.action.IActionModel;

import java.util.List;
import java.util.Map;

public interface IPromptTemplate extends IActionModel {
    String getDisplayName();

    String getDescription();

    List<PromptInputModel> getInputs();

    PromptInputModel getInput(String name);

    List<PromptOutputModel> getOutputs();

    PromptOutputModel getOutput(String name);

    void applyChatOptions(AiChatOptions chatOptions);

    default IEvalScope prepareInputs(Map<String, Object> vars) {
        return prepareInputs(vars, null);
    }

    IEvalScope prepareInputs(Map<String, Object> vars, IEvalContext ctx);

    String generatePrompt(IEvalScope scope);

    void processChatResponse(AiChatExchange chatResponse, IEvalScope scope);
}