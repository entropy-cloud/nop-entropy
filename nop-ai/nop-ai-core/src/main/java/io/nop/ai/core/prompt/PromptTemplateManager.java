package io.nop.ai.core.prompt;

import io.nop.ai.core.AiCoreConstants;
import io.nop.core.resource.component.ResourceComponentManager;

public class PromptTemplateManager implements IPromptTemplateManager {

    @Override
    public IPromptTemplate getPromptTemplate(String promptName) {
        String path = normalizePromptPath(promptName);
        return loadPromptTemplateFromPath(path);
    }

    @Override
    public IPromptTemplate loadPromptTemplateFromPath(String promptPath) {
        return (IPromptTemplate) ResourceComponentManager.instance().loadComponentModel(promptPath);
    }

    protected String normalizePromptPath(String promptName) {
        return "/nop/ai/prompts/" + promptName + AiCoreConstants.POSTFIX_PROMPT_YAML;
    }
}