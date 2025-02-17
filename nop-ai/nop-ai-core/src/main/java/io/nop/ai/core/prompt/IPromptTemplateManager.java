package io.nop.ai.core.prompt;

public interface IPromptTemplateManager {
    IPromptTemplate getPromptTemplate(String promptName);

    IPromptTemplate loadPromptTemplateFromPath(String promptPath);
}
