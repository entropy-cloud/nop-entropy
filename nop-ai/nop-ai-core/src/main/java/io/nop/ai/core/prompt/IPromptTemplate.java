package io.nop.ai.core.prompt;

import java.util.List;
import java.util.Map;

public interface IPromptTemplate {
    List<PromptVarModel> getVars();

    String generatePrompt(Map<String, Object> vars);
}