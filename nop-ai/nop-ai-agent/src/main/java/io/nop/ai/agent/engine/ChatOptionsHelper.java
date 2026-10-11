package io.nop.ai.agent.engine;

import io.nop.ai.api.chat.ChatOptions;
import io.nop.ai.core.model.ChatOptionsModel;

public class ChatOptionsHelper {

    public static ChatOptions toChatOptions(ChatOptionsModel model) {
        if (model == null)
            return null;
        ChatOptions options = new ChatOptions();
        if (model.getProvider() != null)
            options.setProvider(model.getProvider());
        if (model.getModel() != null)
            options.setModel(model.getModel());
        if (model.getTemperature() != null)
            options.setTemperature(model.getTemperature());
        if (model.getTopP() != null)
            options.setTopP(model.getTopP());
        if (model.getMaxTokens() != null)
            options.setMaxTokens(model.getMaxTokens());
        // P1-6.7-006: the model-declared context window maps to the
        // independent maxContextTokens field (never to maxTokens, which is
        // the generation cap).
        if (model.getContextLength() != null)
            options.setMaxContextTokens(model.getContextLength());
        return options;
    }
}
