package io.nop.ai.core.prompt;

import io.nop.ai.core.api.chat.AiChatOptions;
import io.nop.ai.core.api.messages.AiChatResponse;
import io.nop.ai.core.model.PromptInputModel;
import io.nop.ai.core.model.PromptOutputModel;

import java.util.List;
import java.util.Map;

public interface IPromptTemplate {
    List<PromptInputModel> getInputs();

    List<PromptOutputModel> getOutputs();

    void applyChatOptions(AiChatOptions chatOptions);

    String generatePrompt(Map<String, Object> vars);

    void processChatResponse(AiChatResponse chatResponse);
}