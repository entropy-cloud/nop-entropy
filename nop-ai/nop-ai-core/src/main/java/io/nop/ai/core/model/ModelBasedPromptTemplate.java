package io.nop.ai.core.model;

import io.nop.ai.core.AiCoreConstants;
import io.nop.ai.core.api.chat.AiChatOptions;
import io.nop.ai.core.api.messages.AiChatExchange;
import io.nop.ai.core.prompt.IPromptTemplate;
import io.nop.ai.core.prompt.node.IPromptSyntaxNode;
import io.nop.ai.core.prompt.node.PromptSyntaxParser;
import io.nop.api.core.beans.ErrorBean;
import io.nop.api.core.convert.ConvertHelper;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.Guard;
import io.nop.commons.util.StringHelper;
import io.nop.core.context.IEvalContext;
import io.nop.core.exceptions.ErrorMessageManager;
import io.nop.core.lang.eval.IEvalFunction;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.lang.json.JsonTool;
import io.nop.core.lang.xml.XNode;
import io.nop.core.model.object.DynamicObject;
import io.nop.core.reflect.bean.BeanTool;
import io.nop.core.type.PredefinedGenericTypes;
import io.nop.markdown.model.MarkdownDocument;
import io.nop.xlang.api.XLang;
import io.nop.xlang.xdef.IXDefinition;
import io.nop.xlang.xdsl.DslModelParser;
import io.nop.xlang.xdsl.XDslCleaner;
import io.nop.xlang.xdsl.XDslKeys;
import io.nop.xlang.xdsl.XDslValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

import static io.nop.ai.core.AiCoreErrors.ARG_INPUT_NAME;
import static io.nop.ai.core.AiCoreErrors.ARG_OUTPUT_NAME;
import static io.nop.ai.core.AiCoreErrors.ERR_AI_MANDATORY_INPUT_IS_EMPTY;
import static io.nop.ai.core.AiCoreErrors.ERR_AI_MANDATORY_OUTPUT_IS_EMPTY;

public class ModelBasedPromptTemplate implements IPromptTemplate {
    static final Logger LOG = LoggerFactory.getLogger(ModelBasedPromptTemplate.class);

    private final PromptModel promptModel;

    public ModelBasedPromptTemplate(PromptModel promptModel) {
        this.promptModel = promptModel;
    }

    @Override
    public String getDisplayName() {
        return promptModel.getDisplayName();
    }

    @Override
    public String getDescription() {
        return promptModel.getDescription();
    }

    @Override
    public List<PromptInputModel> getInputs() {
        return promptModel.getInputs();
    }

    @Override
    public PromptInputModel getInput(String name) {
        return promptModel.getInput(name);
    }

    @Override
    public List<PromptOutputModel> getOutputs() {
        return promptModel.getOutputs();
    }

    @Override
    public PromptOutputModel getOutput(String name) {
        return promptModel.getOutput(name);
    }


    @Override
    public String getName() {
        return promptModel.getName();
    }

    @Override
    public IEvalScope prepareInputs(Map<String, Object> vars, IEvalContext ctx) {
        IEvalScope scope;
        if (ctx == null) {
            scope = XLang.newEvalScope(vars);
        } else {
            scope = ctx.getEvalScope().newChildScope(vars);
        }
        scope.setLocalValue(AiCoreConstants.SYS_VAR_PROMPT_MODEL, promptModel);

        if (getInputs() != null) {
            for (PromptInputModel input : getInputs()) {
                String name = input.getName();
                if (input.isOptional()) {
                    if (!scope.containsLocalValue(name)) {
                        IEvalFunction fn = input.getDefaultExpr();
                        if (fn != null) {
                            Object value = fn.call0(null, scope);
                            scope.setLocalValue(name, value);
                        } else {
                            scope.setLocalValue(input.getName(), null);
                        }
                    }
                }

                Object value = scope.getLocalValue(name);
                if (input.isMandatory()) {
                    if (StringHelper.isEmptyObject(value)) {
                        throw new NopException(ERR_AI_MANDATORY_INPUT_IS_EMPTY)
                                .source(input).param(ARG_INPUT_NAME, name);
                    }
                } else if (!scope.containsLocalValue(name)) {
                    scope.setLocalValue(name, value);
                }
            }
        }

        if (promptModel.getPreProcess() != null)
            promptModel.getPreProcess().call0(null, scope);

        return scope;
    }

    @Override
    public String generatePrompt(IEvalScope scope) {
        StringBuilder sb = new StringBuilder();
        promptModel.getTemplate().accept(new IPromptSyntaxNode.IPromptSyntaxNodeVisitor() {
            @Override
            public void visitText(PromptSyntaxParser.TextNode expr) {
                sb.append(expr.getText());
            }

            @Override
            public void visitVariable(PromptSyntaxParser.VariableNode expr) {
                String name = expr.getVarName();
                Object value = scope.getValueByPropPath(name);
                if (value != null)
                    sb.append(value);
            }
        });
        return sb.toString();
    }

    @Override
    public void applyChatOptions(AiChatOptions chatOptions) {
        ChatOptionsModel optionsModel = promptModel.getDefaultChatOptions();
        if (optionsModel != null) {
            if (chatOptions.getModel() == null)
                chatOptions.setModel(optionsModel.getModel());

            if (chatOptions.getTemperature() == null)
                chatOptions.setTemperature(optionsModel.getTemperature());

            if (chatOptions.getTopP() == null)
                chatOptions.setTopP(optionsModel.getTopP());

            if (chatOptions.getTopK() == null)
                chatOptions.setTopK(optionsModel.getTopK());

            if (chatOptions.getContextLength() == null)
                chatOptions.setContextLength(optionsModel.getContextLength());

            if (chatOptions.getStop() == null)
                chatOptions.setStop(optionsModel.getStop());

            if (chatOptions.getSeed() == null)
                chatOptions.setSeed(optionsModel.getSeed());

        }
    }

    @Override
    public void processChatResponse(AiChatExchange chatResponse, IEvalScope scope) {
        parseOutputs(chatResponse, false, scope);

        IEvalFunction fn = promptModel.getPostProcess();
        if (fn != null)
            fn.call1(null, chatResponse, scope);

        parseOutputs(chatResponse, true, scope);
    }

    void parseOutputs(AiChatExchange chatResponse, boolean afterPostProcess, IEvalScope scope) {
        if (getOutputs() != null) {
            for (PromptOutputModel output : getOutputs()) {
                if (output.isParseAfterPostProcess() == afterPostProcess) {
                    if (isAllowParse(chatResponse, output, scope)) {
                        Object value = parseOutput(chatResponse, output, scope);
                        chatResponse.setOutput(output.getName(), value);
                    } else if (output.getDefaultExpr() != null) {
                        Object value = output.getDefaultExpr().call1(null, chatResponse, scope);
                        chatResponse.setOutput(output.getName(), value);
                    } else {
                        chatResponse.setOutput(output.getName(), null);
                    }
                }
            }
        }
    }

    protected boolean isAllowParse(AiChatExchange chatResponse, PromptOutputModel output, IEvalScope scope) {
        if (chatResponse.isInvalid() && output.isSkipWhenResponseInvalid())
            return false;
        if (output.getWhen() != null) {
            boolean b = ConvertHelper.toTruthy(output.getWhen().call1(null, chatResponse, scope));
            return b;
        }
        return true;
    }

    protected Object parseOutput(AiChatExchange chatResponse, PromptOutputModel output, IEvalScope scope) {
        Object value;
        if (output.getFormat() == PromptOutputFormat.xml) {
            value = chatResponse.parseXmlContent();
        } else if (output.getFormat() == PromptOutputFormat.json) {
            value = chatResponse.parseJsonContent();
        } else if (output.getFormat() == PromptOutputFormat.yaml) {
            value = chatResponse.parseYamlContent();
        } else if (output.getFormat() == PromptOutputFormat.markdown) {
            value = chatResponse.parseMarkdownContent();
        } else if (output.getFormat() == PromptOutputFormat.code) {
            value = chatResponse.parseCodeBlock(output.getCodeLang());
        } else if (output.getParseFromResponse() != null) {
            PromptOutputParseModel parseModel = output.getParseFromResponse();
            Guard.notNull(parseModel, "parseFromResponse");

            if (parseModel.getParseFunction() != null) {
                value = parseModel.getParseFunction().call1(null, chatResponse, scope);
            } else if (parseModel.getBlockStartMarker() != null && parseModel.getBlockEndMarker() != null) {
                value = chatResponse.getBlock(parseModel.getBlockStartMarker(), parseModel.getBlockEndMarker(),
                        parseModel.isStartMarkerOptional(), output.isOptional());
                if (parseModel.isIncludeStartMarker() || parseModel.isIncludeEndMarker()) {
                    value = (parseModel.isIncludeStartMarker() ? parseModel.getBlockStartMarker() : "")
                            + value + (parseModel.isIncludeEndMarker() ? parseModel.getBlockEndMarker() : "");
                }
            } else if (parseModel.getContainsText() != null) {
                value = chatResponse.contentContains(parseModel.getContainsText());
            } else {
                throw new IllegalArgumentException("unsupported parseFromResponse: " + parseModel);
            }
        } else {
            value = chatResponse.getContent();
        }

        return validateValue(chatResponse, output, value, scope);
    }

    protected Object validateValue(AiChatExchange chatResponse, PromptOutputModel output, Object value, IEvalScope scope) {
        try {
            if (output.getValueNormalizer() != null) {
                value = output.getValueNormalizer().call2(null, value, chatResponse, scope);
            }
            if (output.getFormat() == PromptOutputFormat.yaml) {
                validateYaml((Map<String, Object>) value, output);
            } else if (value instanceof XNode) {
                XNode node = (XNode) value;
                validateXml(node, output);
            } else if (value instanceof MarkdownDocument) {
                MarkdownDocument doc = (MarkdownDocument) value;
                validateMarkdown(doc, output);
            }

            if (output.getType() != null) {
                if (value instanceof XNode) {
                    XNode node = (XNode) value;

                    if (output.getType() == PredefinedGenericTypes.STRING_TYPE) {
                        value = node.html();
                    } else if (output.getType().isMapLike()) {
                        value = transformNodeToMap(node, output);
                    } else {
                        value = transformNodeToMap(node, output);
                        value = BeanTool.castBeanToType(value, output.getType());
                    }
                } else if (value instanceof MarkdownDocument) {
                    MarkdownDocument doc = (MarkdownDocument) value;
                    if (output.getType() == PredefinedGenericTypes.STRING_TYPE) {
                        value = doc.toText(true);
                    }
                } else if (output.getType() == PredefinedGenericTypes.STRING_TYPE) {
                    if (value != null && !(value instanceof String))
                        value = JsonTool.serialize(value, true);
                } else {
                    value = BeanTool.castBeanToType(value, output.getType());
                }
            }

            if (output.getOutputBuilder() != null)
                value = output.getOutputBuilder().call2(null, value, chatResponse, scope);

        } catch (Exception e) {
            LOG.info("nop.err.ai.parse-output-failed:name={},value={}", output.getName(), value, e);
            if (!chatResponse.isInvalid()) {
                ErrorBean errorBean = ErrorMessageManager.instance().buildErrorMessage(null, e);
                chatResponse.setInvalid(true);
                chatResponse.setInvalidReason(errorBean);
            }
        }

        if (output.isMandatory() && StringHelper.isEmptyObject(value)) {
            LOG.info("nop.err.ai.mandatory-output-value-is-empty:name={}", output.getName());
            if (!chatResponse.isInvalid()) {
                chatResponse.setInvalid(true);
                ErrorBean errorBean = new ErrorBean(ERR_AI_MANDATORY_OUTPUT_IS_EMPTY.getErrorCode())
                        .param(ARG_OUTPUT_NAME, output.getName());
                chatResponse.setInvalidReason(errorBean);
            }
        }

        return value;
    }

    protected void validateYaml(Map<String, Object> data, PromptOutputModel output) {

    }

    protected void validateXml(XNode node, PromptOutputModel output) {
        if (output.getXdefObj() != null) {
            XDslCleaner.INSTANCE.clean(node, output.getXdefObj());
            new XDslValidator(XDslKeys.DEFAULT).validate(node, output.getXdefObj(), true);
            node.setAttr(XDslKeys.DEFAULT.SCHEMA, output.getXdefPath());
        }
    }

    protected void validateMarkdown(MarkdownDocument doc, PromptOutputModel output) {
        if (output.getMarkdownTpl() != null)
            doc.matchTpl(output.getMarkdownTpl(), true);
    }

    protected Map<String, Object> transformNodeToMap(XNode node, PromptOutputModel output) {
        IXDefinition xdef = output.getXdefObj();
        if (xdef == null)
            return (Map<String, Object>) node.toXJson();

        DynamicObject obj = (DynamicObject) new DslModelParser().ignoreUnknown(true).parseWithXDef(xdef, node);
        return (Map<String, Object>) JsonTool.serializeToJson(obj);
    }
}
