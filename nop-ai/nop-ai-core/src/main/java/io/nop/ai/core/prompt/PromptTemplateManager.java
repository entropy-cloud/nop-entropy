package io.nop.ai.core.prompt;

import io.nop.ai.core.AiCoreConstants;
import io.nop.core.resource.component.ResourceComponentManager;

public class PromptTemplateManager implements IPromptTemplateManager {

    @Override
    public IPromptTemplate getPromptTemplate(String model, String promptName) {
        String path = normalizePromptPath(model, promptName);
        return (IPromptTemplate) ResourceComponentManager.instance().loadComponentModel(path);
    }

    protected String normalizePromptPath(String model, String promptName) {
        // 带有名字空间
        if (promptName.indexOf(':') > 0)
            return promptName;

        if (promptName.endsWith(AiCoreConstants.POSTFIX_PROMPT_XML)
                || promptName.endsWith(AiCoreConstants.POSTFIX_PROMPT_YAML))
            return promptName;

        return "/nop/ai/prompts/" + model + "/" + promptName + AiCoreConstants.POSTFIX_PROMPT_YAML;
    }
}