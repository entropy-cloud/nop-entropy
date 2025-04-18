package io.nop.ai.core.model;

import io.nop.ai.core.api.chat.AiChatOptions;
import io.nop.ai.core.api.messages.AiChatResponse;
import io.nop.ai.core.model._gen._PromptModel;
import io.nop.ai.core.prompt.IPromptTemplate;
import io.nop.api.core.exceptions.NopException;
import io.nop.commons.util.StringHelper;
import io.nop.core.lang.eval.IEvalFunction;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.xlang.api.XLang;

import java.util.Map;

import static io.nop.ai.core.AiCoreErrors.ARG_INPUT_NAME;
import static io.nop.ai.core.AiCoreErrors.ERR_AI_MANDATORY_INPUT_IS_EMPTY;

public class PromptModel extends _PromptModel implements IPromptTemplate {
    public PromptModel() {

    }

    @Override
    public String generatePrompt(Map<String, Object> vars) {
        IEvalScope scope = XLang.newEvalScope(vars);
        prepareInputs(scope);
        return getTemplate().generateText(scope);
    }

    private void prepareInputs(IEvalScope scope) {
        if (getInputs() != null) {
            for (PromptInputModel input : getInputs()) {
                String name = input.getName();
                if (input.isOptional()) {
                    if (!scope.containsLocalValue(name)) {
                        IEvalFunction fn = input.getDefaultExpr();
                        if (fn != null) {
                            Object value = fn.call0(null, scope);
                            scope.setLocalValue(name, value);
                        }
                    }
                }

                Object value = scope.getLocalValue(name);
                if (input.isMandatory()) {
                    if (StringHelper.isEmptyObject(value)) {
                        throw new NopException(ERR_AI_MANDATORY_INPUT_IS_EMPTY)
                                .source(input).param(ARG_INPUT_NAME, name);
                    }
                }
            }
        }
    }

    @Override
    public void processChatResponse(AiChatResponse chatResponse) {
        parseOutputs(chatResponse, true);

        IEvalFunction fn = this.getProcessChatResponse();
        if (fn != null)
            fn.call1(null, chatResponse, XLang.newEvalScope());

        parseOutputs(chatResponse, false);
    }


    void parseOutputs(AiChatResponse chatResponse, boolean beforeProcess) {
        if (getOutputs() != null) {
            for (PromptOutputModel output : getOutputs()) {
                if (output.isParseBeforeProcess() == beforeProcess) {
                    if (output.getParseFromResponse() != null) {
                        PromptOutputParseModel parseModel = output.getParseFromResponse();
                        if (parseModel.getBlockBegin() != null && parseModel.getBlockEnd() != null) {
                            chatResponse.parseBlock()
                        }
                    }
                }
            }
        }
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
