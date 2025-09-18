package io.nop.ai.core.model;

import io.nop.ai.core.AiCoreConstants;
import io.nop.ai.core.model._gen._PromptModel;
import io.nop.ai.core.prompt.IPromptTemplate;
import io.nop.ai.core.prompt.node.IPromptSyntaxNode;
import io.nop.ai.core.prompt.node.PromptSyntaxParser;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.IComponentModel;
import io.nop.api.core.util.INeedInit;
import io.nop.api.core.util.SourceLocation;
import io.nop.commons.util.StringHelper;
import io.nop.core.resource.ResourceHelper;

import static io.nop.ai.core.AiCoreErrors.ARG_DEFINED_VARS;
import static io.nop.ai.core.AiCoreErrors.ARG_PROMPT_NAME;
import static io.nop.ai.core.AiCoreErrors.ARG_VAR_NAME;
import static io.nop.ai.core.AiCoreErrors.ERR_AI_PROMPT_USE_UNDEFINED_VAR;

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

    public String getXdefForResult() {
        PromptOutputModel output = getOutput("RESULT");
        return output == null ? null : output.getXdefForAi();
    }

    public String getMarkdownTplForResult() {
        PromptOutputModel output = getOutput("RESULT");
        return output == null ? null : output.getMarkdownTpl().getText();
    }

    public String getMarkdownTplWithoutDetailForResult() {
        PromptOutputModel output = getOutput("RESULT");
        return output == null ? null : output.getMarkdownTplWithoutDetail().getText();
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

        validateTemplate(this.getTemplate());
    }

    private void validateTemplate(IPromptSyntaxNode template) {
        if (template == null)
            throw new IllegalArgumentException("prompt template is null");

        template.accept(new IPromptSyntaxNode.IPromptSyntaxNodeVisitor() {
            @Override
            public void visitVariable(PromptSyntaxParser.VariableNode expr) {
                String varName = StringHelper.firstPart(expr.getVarName(), '.');
                // 以_为前缀的是系统变量或者preProcess段中生成临时变量，不需要事先声明
                if (!varName.startsWith("_") && getInput(varName) == null) {
                    throw new NopException(ERR_AI_PROMPT_USE_UNDEFINED_VAR)
                            .source(expr).param(ARG_VAR_NAME, expr.getVarName())
                            .param(ARG_PROMPT_NAME, getName())
                            .param(ARG_DEFINED_VARS, keySet_inputs());
                }
            }
        });
    }
}
