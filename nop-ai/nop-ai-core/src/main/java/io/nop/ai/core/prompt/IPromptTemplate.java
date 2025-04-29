package io.nop.ai.core.prompt;

import io.nop.ai.core.api.chat.AiChatOptions;
import io.nop.ai.core.api.messages.AiChatResponse;
import io.nop.ai.core.model.PromptInputModel;
import io.nop.ai.core.model.PromptOutputModel;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.xlang.xdsl.action.IActionModel;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public interface IPromptTemplate extends IActionModel {
    String getDisplayName();

    String getDescription();

    List<PromptInputModel> getInputs();

    PromptInputModel getInput(String name);

    List<PromptOutputModel> getOutputs();

    PromptOutputModel getOutput(String name);

    default List<String> getOutputNames() {
        return getOutputs().stream().map(PromptOutputModel::getName).collect(Collectors.toList());
    }

    void applyChatOptions(AiChatOptions chatOptions);

    IEvalScope prepareInputs(Map<String, Object> vars);

    String generatePrompt(IEvalScope scope);

    void processChatResponse(AiChatResponse chatResponse, IEvalScope scope);
}