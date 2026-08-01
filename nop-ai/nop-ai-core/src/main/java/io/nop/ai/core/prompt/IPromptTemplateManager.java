package io.nop.ai.core.prompt;

public interface IPromptTemplateManager {
    /**
     * Resolves a prompt template by its logical name (e.g. from the {@code /nop/ai/prompts/} directory).
     *
     * @param promptName the logical prompt name
     * @return the resolved prompt template
     */
    IPromptTemplate getPromptTemplate(String promptName);

    /**
     * Loads a prompt template from an explicit model resource path.
     *
     * @param promptPath the prompt model resource path (e.g. {@code /nop/ai/prompts/foo.prompt.yaml})
     * @return the loaded prompt template
     */
    IPromptTemplate loadPromptTemplateFromPath(String promptPath);
}
