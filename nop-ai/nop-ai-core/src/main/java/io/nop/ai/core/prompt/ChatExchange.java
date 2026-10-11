package io.nop.ai.core.prompt;

import io.nop.ai.api.chat.ChatRequest;
import io.nop.ai.api.chat.ChatResponse;
import io.nop.ai.core.AiCoreConstants;
import io.nop.ai.core.response.CodeResponseParser;
import io.nop.ai.core.response.JsonResponseParser;
import io.nop.ai.core.response.MarkdownResponseParser;
import io.nop.ai.core.response.XmlResponseParser;
import io.nop.ai.core.response.YamlResponseParser;
import io.nop.api.core.annotations.data.DataBean;
import io.nop.api.core.beans.ErrorBean;
import io.nop.api.core.convert.ConvertHelper;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.exceptions.NopRebuildException;
import io.nop.commons.text.SourceCodeBlock;
import io.nop.commons.util.StringHelper;
import io.nop.core.lang.xml.XNode;
import io.nop.markdown.model.MarkdownDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

import static io.nop.ai.core.NopAiCoreErrors.ARG_BLOCK_BEGIN;
import static io.nop.ai.core.NopAiCoreErrors.ARG_BLOCK_END;
import static io.nop.ai.core.NopAiCoreErrors.ARG_CONTENT;
import static io.nop.ai.core.NopAiCoreErrors.ERR_AI_INVALID_RESPONSE;
import static io.nop.ai.core.NopAiCoreErrors.ERR_AI_RESULT_IS_EMPTY;
import static io.nop.ai.core.NopAiCoreErrors.ERR_AI_RESULT_NO_EXPECTED_PART;

/**
 * Prompt execution exchange over the {@code IChatService} stack: the
 * {@link ChatRequest} sent to the LLM, the {@link ChatResponse} returned by it,
 * and the outputs parsed from the response by a prompt template
 * (see {@link IPromptTemplate#processChatResponse(ChatExchange, IEvalScope)}).
 *
 * <p>Scripts (prompt yaml {@code postProcess} / {@code when} expressions bound to
 * the {@code chatExchange} argument in {@code prompt.xdef}, and the generated
 * {@code res.validate().outputs} / {@code res.validate().resultValue} accessors)
 * consume the {@code outputs} / {@code validate()} / {@code resultValue} surface.
 */
@DataBean
public class ChatExchange {
    static final Logger LOG = LoggerFactory.getLogger(ChatExchange.class);

    private ChatRequest request;
    private ChatResponse response;

    /**
     * 接下响应消息得到的结果对象
     */
    private Map<String, Object> outputs;

    /**
     * 解析output失败会设置invalid为true
     */
    private boolean invalid;
    private ErrorBean invalidReason;

    public ChatExchange() {
    }

    public ChatExchange(ChatRequest request, ChatResponse response) {
        this.request = request;
        this.response = response;
    }

    public ChatRequest getRequest() {
        return request;
    }

    public void setRequest(ChatRequest request) {
        this.request = request;
    }

    public ChatResponse getResponse() {
        return response;
    }

    public void setResponse(ChatResponse response) {
        this.response = response;
    }

    public String getContent() {
        if (response == null)
            return null;
        return response.outputText();
    }

    public void setContent(String content) {
        ChatResponse response = makeResponse();
        response.setMessages(null);
        response.addMessage(new io.nop.ai.api.chat.messages.ChatAssistantMessage(content));
    }

    public String getFullContent() {
        if (response == null)
            return null;
        return response.getFullContent();
    }

    public boolean isEmpty() {
        return StringHelper.isEmpty(getContent());
    }

    public boolean isInvalid() {
        return invalid;
    }

    public void setInvalid(boolean invalid) {
        this.invalid = invalid;
    }

    public ErrorBean getInvalidReason() {
        return invalidReason;
    }

    public void setInvalidReason(ErrorBean invalidReason) {
        this.invalidReason = invalidReason;
    }

    public boolean isValid() {
        return !invalid;
    }

    public ChatExchange validate() {
        if (!isValid()) {
            if (invalidReason != null)
                throw NopRebuildException.rebuild(invalidReason);
            throw new NopException(ERR_AI_INVALID_RESPONSE)
                    .param(ARG_CONTENT, StringHelper.limitLen(getContent(), 255));
        }
        return this;
    }

    public ChatResponse makeResponse() {
        if (response == null)
            response = new ChatResponse();
        return response;
    }

    public Map<String, Object> getOutputs() {
        return outputs;
    }

    public void setOutputs(Map<String, Object> outputs) {
        this.outputs = outputs;
    }

    public Object getOutput(String name) {
        if (outputs == null)
            return null;
        return outputs.get(name);
    }

    public void setOutput(String name, Object value) {
        if (outputs == null)
            outputs = new HashMap<>();
        outputs.put(name, value);
    }

    public Object getResultValue() {
        return getOutput(AiCoreConstants.OUTPUT_VAR_RESULT);
    }

    public String getResultText() {
        return ConvertHelper.toString(getOutput(AiCoreConstants.OUTPUT_VAR_RESULT));
    }

    public boolean contentContains(String str) {
        String content = getContent();
        if (content == null)
            return false;
        return content.contains(str);
    }

    public XNode parseXmlContent() {
        String content = getContent();
        if (StringHelper.isEmpty(content))
            return null;

        return XmlResponseParser.instance().parseResponse(content);
    }

    public MarkdownDocument parseMarkdownContent() {
        String content = getContent();
        if (StringHelper.isEmpty(content))
            return null;
        return MarkdownResponseParser.instance().parseResponse(content);
    }

    public Object parseJsonContent() {
        String content = getContent();
        if (StringHelper.isEmpty(content))
            return null;
        return JsonResponseParser.instance().parseResponse(content);
    }

    public Map<String, Object> parseYamlContent() {
        String content = getContent();
        if (StringHelper.isEmpty(content))
            return null;
        return YamlResponseParser.instance().parseResponse(content);
    }

    public SourceCodeBlock parseCodeBlock(String lang) {
        String content = getContent();
        if (StringHelper.isEmpty(content))
            return null;
        return CodeResponseParser.instance().parseResponse(content, lang);
    }

    public String getBlock(String blockBegin, String blockEnd) {
        return getBlock(blockBegin, blockEnd, false, false);
    }

    public String getBlock(String blockBegin, String blockEnd, boolean optionalBegin, boolean optional) {
        String content = getContent();
        if (StringHelper.isEmpty(content)) {
            if (optional)
                return null;

            LOG.debug("nop.err.ai.content-is-empty-when-get-block");

            invalidReason = new ErrorBean(ERR_AI_RESULT_IS_EMPTY.getErrorCode())
                    .param(ARG_BLOCK_BEGIN, blockBegin)
                    .param(ARG_BLOCK_END, blockEnd);
            setInvalid(true);
            return null;
        }

        int[] markPos = indexOfMark(content, 0, blockBegin);
        if (markPos == null) {
            if (optional)
                return null;
            if (!optionalBegin) {
                LOG.debug("nop.err.ai.missing-block-begin:{}", blockBegin);

                invalidReason = new ErrorBean(ERR_AI_RESULT_NO_EXPECTED_PART.getErrorCode())
                        .param(ARG_BLOCK_BEGIN, blockBegin);
                setInvalid(true);
                return null;
            }
        }

        int pos = markPos == null ? 0 : markPos[1];

        int[] markPos2 = indexOfMark(content, pos, blockEnd);
        if (markPos2 == null) {
            if (optional)
                return null;
            LOG.debug("nop.err.ai.missing-block-end:{}", blockEnd);

            invalidReason = new ErrorBean(ERR_AI_RESULT_NO_EXPECTED_PART.getErrorCode())
                    .param(ARG_BLOCK_END, blockEnd);
            setInvalid(true);
            return null;
        }
        int pos2 = markPos2[0];
        return content.substring(pos, pos2);
    }

    /**
     * Whitespace-tolerant marker lookup carried over verbatim from the legacy
     * chat stack: returns {@code {markStart, contentStartAfterMark}}, or null
     * when the marker is absent.
     */
    static int[] indexOfMark(String content, int start, String mark) {
        int pos = content.indexOf(mark, start);
        if (pos >= 0)
            return new int[]{pos, pos + mark.length()};
        String trimmedMark = mark.trim();
        pos = content.indexOf(trimmedMark, start);
        if (pos >= 0) {
            int pos0 = pos, pos1 = pos + trimmedMark.length();
            if (mark.startsWith("\n")) {
                int pos2 = content.lastIndexOf('\n', pos);
                if (!StringHelper.onlyContainsWhitespace(content.substring(pos2 + 1, pos)))
                    return null;
                pos0 = pos2 + 1;
            }

            if (mark.endsWith("\n")) {
                int pos2 = content.indexOf('\n', pos1);
                if (pos2 < 0)
                    pos2 = content.length();
                if (!StringHelper.onlyContainsWhitespace(content.substring(pos1, pos2)))
                    return null;
                pos1 = pos2;
            }

            return new int[]{pos0, pos1};
        }
        return null;
    }

    @Override
    public String toString() {
        return "ChatExchange{" +
                "invalid=" + invalid +
                ", content='" + getContent() +
                "'}";
    }
}
