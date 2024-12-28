package io.nop.ai.core.prompt;

public interface IPromptTemplateManager {
    IPromptTemplate getPromptTemplate(String model, String promptName);
}
