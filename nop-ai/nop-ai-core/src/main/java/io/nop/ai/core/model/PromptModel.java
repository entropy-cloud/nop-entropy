package io.nop.ai.core.model;

import io.nop.ai.core.AiCoreConstants;
import io.nop.ai.core.model._gen._PromptModel;
import io.nop.ai.core.prompt.IPromptTemplate;
import io.nop.api.core.util.IComponentModel;
import io.nop.api.core.util.INeedInit;
import io.nop.api.core.util.SourceLocation;
import io.nop.core.resource.ResourceHelper;

public class PromptModel extends _PromptModel implements INeedInit, IComponentModel {
    private String name;
    private IPromptTemplate promptTemplate;

    public PromptModel() {

    }

    public IPromptTemplate getPromptTemplate() {
        return promptTemplate;
    }

    public void setPromptTemplate(IPromptTemplate promptTemplate) {
        this.promptTemplate = promptTemplate;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @Override
    public void init() {
        if (getOutputs() != null) {
            for (PromptOutputModel output : getOutputs()) {
                output.init();
            }
        }

        if (name == null) {
            SourceLocation loc = getLocation();
            if (loc != null) {
                String path = ResourceHelper.getStdPath(loc.getPath());
                if (path.startsWith(AiCoreConstants.PATH_AI_PROMPTS)) {
                    String promptName = null;
                    if (path.endsWith(AiCoreConstants.POSTFIX_PROMPT_XML)) {
                        promptName = path.substring(AiCoreConstants.PATH_AI_PROMPTS.length(), path.length() - AiCoreConstants.POSTFIX_PROMPT_XML.length());
                    } else if (path.endsWith(AiCoreConstants.POSTFIX_PROMPT_YAML)) {
                        promptName = path.substring(AiCoreConstants.PATH_AI_PROMPTS.length(), path.length() - AiCoreConstants.POSTFIX_PROMPT_YAML.length());
                    }
                    this.name = promptName;
                }
            }
        }
    }
}
