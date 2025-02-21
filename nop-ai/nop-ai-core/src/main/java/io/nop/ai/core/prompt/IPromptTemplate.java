package io.nop.ai.core.prompt;

import io.nop.ai.core.api.chat.AiChatOptions;
import io.nop.ai.core.api.messages.AiChatResponse;
import io.nop.ai.core.model.PromptVarModel;

import java.util.List;
import java.util.Map;

public interface IPromptTemplate {
    List<PromptVarModel> getVars();

    void applyChatOptions(AiChatOptions chatOptions);

    String generatePrompt(Map<String, Object> vars);

    void processChatResponse(AiChatResponse chatResponse);
}