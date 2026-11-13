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

import static io.nop.ai.core.NopAiCoreErrors.ARG_DEFINED_VARS;
import static io.nop.ai.core.NopAiCoreErrors.ARG_PROMPT_NAME;
import static io.nop.ai.core.NopAiCoreErrors.ARG_VAR_NAME;
import static io.nop.ai.core.NopAiCoreErrors.ERR_AI_PROMPT_TEMPLATE_NULL;
import static io.nop.ai.core.NopAiCoreErrors.ERR_AI_PROMPT_USE_UNDEFINED_VAR;

public class PromptModel extends _PromptModel implements INeedInit, IComponentModel {
    // 3.1-MA-13（plan 2555 裁定 C / C1-001）：promptTemplate 由 PromptTemplateManager
    // 在请求热路径惰性写入，volatile 化保证跨线程可见。setter 契约：仅装载/首用期写入
    // 一次（记忆化），运行期换装不在契约内。
    private volatile String name;
    private volatile IPromptTemplate promptTemplate;

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
            throw new NopException(ERR_AI_PROMPT_TEMPLATE_NULL);

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
