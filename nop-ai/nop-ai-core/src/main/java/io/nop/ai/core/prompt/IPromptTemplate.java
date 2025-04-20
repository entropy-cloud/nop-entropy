package io.nop.ai.core.prompt;

import io.nop.ai.core.api.chat.AiChatOptions;
import io.nop.ai.core.api.messages.AiChatResponse;
import io.nop.ai.core.model.PromptInputModel;
import io.nop.ai.core.model.PromptOutputModel;
import io.nop.core.lang.eval.IEvalScope;

import java.util.List;
import java.util.Map;

public interface IPromptTemplate {
    List<PromptInputModel> getInputs();

    List<PromptOutputModel> getOutputs();

    void applyChatOptions(AiChatOptions chatOptions);

    IEvalScope prepareInputs(Map<String, Object> vars);

    String generatePrompt(IEvalScope scope);

    void processChatResponse(AiChatResponse chatResponse, IEvalScope scope);
}