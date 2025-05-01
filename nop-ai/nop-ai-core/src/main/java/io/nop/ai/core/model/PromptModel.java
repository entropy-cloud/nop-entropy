package io.nop.ai.core.model;

import io.nop.ai.core.AiCoreConstants;
import io.nop.ai.core.api.chat.AiChatOptions;
import io.nop.ai.core.api.messages.AiChatResponse;
import io.nop.ai.core.model._gen._PromptModel;
import io.nop.ai.core.prompt.IPromptTemplate;
import io.nop.api.core.beans.ErrorBean;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.Guard;
import io.nop.api.core.util.IComponentModel;
import io.nop.api.core.util.INeedInit;
import io.nop.api.core.util.SourceLocation;
import io.nop.commons.util.StringHelper;
import io.nop.core.exceptions.ErrorMessageManager;
import io.nop.core.lang.eval.IEvalFunction;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.lang.json.JsonTool;
import io.nop.core.lang.xml.XNode;
import io.nop.core.model.object.DynamicObject;
import io.nop.core.reflect.bean.BeanTool;
import io.nop.core.resource.ResourceHelper;
import io.nop.core.type.PredefinedGenericTypes;
import io.nop.markdown.simple.MarkdownDocument;
import io.nop.xlang.api.XLang;
import io.nop.xlang.xdef.IXDefinition;
import io.nop.xlang.xdsl.DslModelParser;
import io.nop.xlang.xdsl.XDslKeys;
import io.nop.xlang.xdsl.XDslValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

import static io.nop.ai.core.AiCoreErrors.ARG_INPUT_NAME;
import static io.nop.ai.core.AiCoreErrors.ARG_OUTPUT_NAME;
import static io.nop.ai.core.AiCoreErrors.ERR_AI_MANDATORY_INPUT_IS_EMPTY;
import static io.nop.ai.core.AiCoreErrors.ERR_AI_MANDATORY_OUTPUT_IS_EMPTY;

public class PromptModel extends _PromptModel implements IPromptTemplate, INeedInit, IComponentModel {
    static final Logger LOG = LoggerFactory.getLogger(PromptModel.class);
    private String name;

    public PromptModel() {

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

    @Override
    public IEvalScope prepareInputs(Map<String, Object> vars) {
        IEvalScope scope = XLang.newEvalScope(vars);
        scope.setLocalValue(AiCoreConstants.VAR_PROMPT_MODEL, this);

        if (getInputs() != null) {
            for (PromptInputModel input : getInputs()) {
                String name = input.getName();
                if (input.isOptional()) {
                    if (!scope.containsLocalValue(name)) {
                        IEvalFunction fn = input.getDefaultExpr();
                        if (fn != null) {
                            Object value = fn.call0(null, scope);
                            scope.setLocalValue(name, value);
                        }
                    }
                }

                Object value = scope.getLocalValue(name);
                if (input.isMandatory()) {
                    if (StringHelper.isEmptyObject(value)) {
                        throw new NopException(ERR_AI_MANDATORY_INPUT_IS_EMPTY)
                                .source(input).param(ARG_INPUT_NAME, name);
                    }
                }
            }
        }
        return scope;
    }


    @Override
    public String generatePrompt(IEvalScope scope) {
        return getTemplate().generateText(scope);
    }

    @Override
    public void processChatResponse(AiChatResponse chatResponse, IEvalScope scope) {
        if (this.getEndResponseMarker() != null) {
            chatResponse.checkAndRemoveEndLine(this.getEndResponseMarker());
        }

        parseOutputs(chatResponse, true, scope);

        IEvalFunction fn = this.getProcessChatResponse();
        if (fn != null)
            fn.call1(null, chatResponse, scope);

        parseOutputs(chatResponse, false, scope);
    }


    void parseOutputs(AiChatResponse chatResponse, boolean beforeProcess, IEvalScope scope) {
        if (getOutputs() != null) {
            for (PromptOutputModel output : getOutputs()) {

                if (chatResponse.isInvalid() && output.isSkipWhenResponseInvalid())
                    continue;

                if (output.isParseBeforeProcess() == beforeProcess) {
                    Object value = parseOutput(chatResponse, output, scope);
                    chatResponse.setOutput(output.getName(), value);
                }
            }
        }
    }

    protected Object parseOutput(AiChatResponse chatResponse, PromptOutputModel output, IEvalScope scope) {
        Object value;
        if (output.getFormat() == PromptOutputFormat.xml) {
            value = chatResponse.parseXmlContent();
        } else if (output.getFormat() == PromptOutputFormat.json) {
            value = chatResponse.parseJsonContent();
        } else if (output.getFormat() == PromptOutputFormat.markdown) {
            value = chatResponse.parseMarkdownContent();
        } else {
            PromptOutputParseModel parseModel = output.getParseFromResponse();
            Guard.notNull(parseModel, "parseFromResponse");

            if (parseModel.getParser() != null) {
                value = parseModel.getParser().call1(null, chatResponse, scope);
            } else if (parseModel.getBlockBegin() != null && parseModel.getBlockEnd() != null) {
                value = chatResponse.getBlock(parseModel.getBlockBegin(), parseModel.getBlockEnd(),
                        parseModel.isBeginBlockOptional(), output.isOptional());
                if (parseModel.isIncludeBlockBegin() || parseModel.isIncludeBlockEnd()) {
                    value = (parseModel.isIncludeBlockBegin() ? parseModel.getBlockBegin() : "")
                            + value + (parseModel.isIncludeBlockEnd() ? parseModel.getBlockEnd() : "");
                }
            } else if (parseModel.getContains() != null) {
                value = chatResponse.contentContains(parseModel.getContains());
            } else {
                throw new IllegalArgumentException("unsupported parseFromResponse: " + parseModel);
            }
        }

        return validateValue(chatResponse, output, value, scope);
    }

    protected Object validateValue(AiChatResponse chatResponse, PromptOutputModel output, Object value, IEvalScope scope) {
        try {
            if (output.getNormalizer() != null) {
                value = output.getNormalizer().call2(null, value, chatResponse, scope);
            }

            if (value instanceof XNode) {
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
                        value = doc.toText();
                    }
                } else if (output.getType() == PredefinedGenericTypes.STRING_TYPE) {
                    value = JsonTool.serialize(value, true);
                } else {
                    value = BeanTool.castBeanToType(value, output.getType());
                }
            }
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

    protected void validateXml(XNode node, PromptOutputModel output) {
        if (output.getXdefObj() != null) {
            new XDslValidator(XDslKeys.DEFAULT).removeUnknownAttrs(true).validate(node, output.getXdefObj(), true);
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

    @Override
    public void applyChatOptions(AiChatOptions chatOptions) {
        ChatOptionsModel optionsModel = getDefaultChatOptions();
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
}
