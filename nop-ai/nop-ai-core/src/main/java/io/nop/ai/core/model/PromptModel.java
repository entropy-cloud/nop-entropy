package io.nop.ai.core.model;

import io.nop.ai.core.api.chat.AiChatOptions;
import io.nop.ai.core.api.messages.AiChatResponse;
import io.nop.ai.core.model._gen._PromptModel;
import io.nop.ai.core.prompt.IPromptTemplate;
import io.nop.core.lang.eval.IEvalFunction;
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

    @Override
    public void processChatResponse(AiChatResponse chatResponse) {
        IEvalFunction fn = this.getProcessChatResponse();
        if (fn != null)
            fn.call1(null, chatResponse, XLang.newEvalScope());
    }

    @Override
    public void applyChatOptions(AiChatOptions chatOptions) {
        ChatOptionsModel optionsModel = getDefaultChatOptions();
        if (optionsModel != null) {
            if (chatOptions.getModel() == null)
                chatOptions.setModel(optionsModel.getModel());

            if (chatOptions.getTemperature() == null)
                chatOptions.setTemperature(optionsModel.getTemperature());

            if (chatOptions.getTopP() == null)
                chatOptions.setTopP(optionsModel.getTopP());

            if (chatOptions.getTopK() == null)
                chatOptions.setTopK(optionsModel.getTopK());

            if (chatOptions.getContextLength() == null)
                chatOptions.setContextLength(optionsModel.getContextLength());

            if (chatOptions.getStop() == null)
                chatOptions.setStop(optionsModel.getStop());

            if (chatOptions.getSeed() == null)
                chatOptions.setSeed(optionsModel.getSeed());
        }
    }
}
