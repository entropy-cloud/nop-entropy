package io.nop.ai.core.prompt;

import io.nop.ai.core.AiCoreConstants;
import io.nop.ai.core.model.ModelBasedPromptTemplate;
import io.nop.ai.core.model.PromptModel;
import io.nop.core.resource.component.ResourceComponentManager;

public class PromptTemplateManager implements IPromptTemplateManager {

    @Override
    public IPromptTemplate getPromptTemplate(String promptName) {
        String path = normalizePromptPath(promptName);
        return loadPromptTemplateFromPath(path);
    }

    @Override
    public IPromptTemplate loadPromptTemplateFromPath(String promptPath) {
        PromptModel promptModel = (PromptModel) ResourceComponentManager.instance().loadComponentModel(promptPath);
        IPromptTemplate promptTemplate = promptModel.getPromptTemplate();
        if (promptTemplate == null) {
            promptTemplate = new ModelBasedPromptTemplate(promptModel);
            promptModel.setPromptTemplate(promptTemplate);
        }
        return promptTemplate;
    }

    protected String normalizePromptPath(String promptName) {
        return "/nop/ai/prompts/" + promptName + AiCoreConstants.POSTFIX_PROMPT_YAML;
    }
}