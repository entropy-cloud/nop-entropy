/*
 *  Copyright (c) 2023-2025, Agents-Flex (fuhai999@gmail.com).
 *  <p>
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *  <p>
 *  http://www.apache.org/licenses/LICENSE-2.0
 *  <p>
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package io.nop.ai.core.api.messages;


import com.fasterxml.jackson.annotation.JsonIgnore;
import io.nop.ai.core.AiCoreConstants;
import io.nop.ai.core.api.chat.AiChatOptions;
import io.nop.ai.core.persist.DefaultAiChatExchangePersister;
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
import io.nop.commons.util.StringHelper;
import io.nop.core.lang.xml.XNode;
import io.nop.markdown.model.MarkdownCodeBlock;
import io.nop.markdown.model.MarkdownDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static io.nop.ai.core.AiCoreErrors.ARG_BLOCK_BEGIN;
import static io.nop.ai.core.AiCoreErrors.ARG_BLOCK_END;
import static io.nop.ai.core.AiCoreErrors.ARG_CONTENT;
import static io.nop.ai.core.AiCoreErrors.ARG_EXPECTED;
import static io.nop.ai.core.AiCoreErrors.ARG_LINE;
import static io.nop.ai.core.AiCoreErrors.ERR_AI_INVALID_RESPONSE;
import static io.nop.ai.core.AiCoreErrors.ERR_AI_RESULT_INVALID_END_LINE;
import static io.nop.ai.core.AiCoreErrors.ERR_AI_RESULT_IS_EMPTY;
import static io.nop.ai.core.AiCoreErrors.ERR_AI_RESULT_NO_EXPECTED_PART;
import static io.nop.ai.core.api.messages.AiMessage.indexOfMark;

@DataBean
public class AiChatExchange {
    static final Logger LOG = LoggerFactory.getLogger(AiChatExchange.class);

    private long beginTime;
    private String exchangeId;

    /**
     * 实际使用的ChatOptions参数
     */
    private AiChatOptions chatOptions;

    /**
     * 此次消息所对应的prompt
     */
    private Prompt prompt;

    private Integer index;
    private MessageStatus status;

    private AiAssistantMessage response;

    private AiChatUsage usage;

    /**
     * 接下响应消息得到的结果对象
     */
    private Map<String, Object> outputs;


    /**
     * 解析output失败会设置invalid为true
     */
    private boolean invalid;
    private ErrorBean invalidReason;

    public AiChatExchange() {
    }

    public AiChatExchange(AiAssistantMessage response) {
        this.response = response;
    }

    public long getBeginTime() {
        return beginTime;
    }

    public void setBeginTime(long beginTime) {
        this.beginTime = beginTime;
    }

    public int getRetryTimes() {
        if (prompt == null)
            return 0;
        return prompt.getRetryTimes();
    }

    public String getPromptName() {
        return prompt == null ? null : prompt.getName();
    }

    public void clearResponse() {
        this.usage = null;
        this.status = null;
        this.setThink(null);
        this.response = null;
        this.invalid = false;
        this.invalidReason = null;
    }

    public Integer getUsedTime() {
        return usage != null ? usage.getUsedTime() : null;
    }

    public void setUsedTime(Integer usedTime) {
        if (usage == null)
            usage = new AiChatUsage();
        usage.setUsedTime(usedTime);
    }

    public List<AiMessage> getAllMessages(boolean includeSystem) {
        List<AiMessage> ret = new ArrayList<>();
        if (includeSystem) {
            ret.addAll(prompt.getMessages());
        } else {
            for (AiMessage message : prompt.getMessages()) {
                if (!message.isSystemMessage())
                    ret.add(message);
            }
        }
        if (response != null)
            ret.add(response);
        return ret;
    }

    public Integer getPromptTokens() {
        return usage != null ? usage.getPromptTokens() : null;
    }


    public Integer getCompletionTokens() {
        return usage != null ? usage.getCompletionTokens() : null;
    }

    public Integer getTotalTokens() {
        return usage != null ? usage.getTotalTokens() : null;
    }

    public AiChatUsage getUsage() {
        return usage;
    }

    public void setUsage(AiChatUsage usage) {
        this.usage = usage;
    }

    public String getExchangeId() {
        return exchangeId;
    }

    public void setExchangeId(String exchangeId) {
        this.exchangeId = exchangeId;
    }

    @JsonIgnore
    public Map<String, Object> getMetadata() {
        if (prompt == null)
            return null;
        return prompt.getMetadata();
    }

    @JsonIgnore
    public Map<String, Object> getVariables() {
        if (prompt == null)
            return null;
        return prompt.getVariables();
    }

    public AiAssistantMessage makeResponse() {
        if (response == null)
            response = new AiAssistantMessage();
        return response;
    }

    public String getFullContent() {
        String think = getThink();
        if (think == null)
            return getContent();

        StringBuilder sb = new StringBuilder();
        sb.append("<think>\n");
        sb.append(think);
        sb.append("\n</think>\n");

        String content = getContent();
        if (content != null)
            sb.append(content);
        return sb.toString();
    }

    public String getContent() {
        if (response == null)
            return null;
        return response.getContent();
    }

    public void setContent(String content) {
        this.makeResponse().setContent(content);
    }

    @JsonIgnore
    public boolean isEmpty() {
        return StringHelper.isEmpty(getContent()) && outputs == null;
    }

    public String getBlockFromPrompt(String blockBegin, String blockEnd) {
        return getBlockFromPrompt(blockBegin, blockEnd, 0);
    }

    public String getBlockFromPrompt(String blockBegin, String blockEnd, int blockIndex) {
        if (prompt == null)
            return null;

        return prompt.getLastMessage().getBlock(blockBegin, blockEnd, blockIndex);
    }

    public String getThink() {
        if (response == null)
            return null;
        return response.getThink();
    }

    public void setThink(String think) {
        this.makeResponse().setThink(think);
    }

    public AiAssistantMessage getResponse() {
        return response;
    }

    public void setResponse(AiAssistantMessage response) {
        this.response = response;
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

    @JsonIgnore
    public boolean isValid() {
        return !invalid;
    }

    public AiChatExchange validate() {
        if (!isValid()) {
            if (invalidReason != null)
                throw NopRebuildException.rebuild(invalidReason);
            throw new NopException(ERR_AI_INVALID_RESPONSE)
                    .param(ARG_CONTENT, StringHelper.limitLen(getContent(), 255));
        }
        return this;
    }

    public AiChatOptions getChatOptions() {
        return chatOptions;
    }

    public void setChatOptions(AiChatOptions chatOptions) {
        this.chatOptions = chatOptions;
    }

    public AiChatOptions makeChatOptions() {
        if (chatOptions == null)
            chatOptions = new AiChatOptions();
        return chatOptions;
    }

    public Integer getIndex() {
        return index;
    }

    public void setIndex(Integer index) {
        this.index = index;
    }

    public MessageStatus getStatus() {
        return status;
    }

    public void setStatus(MessageStatus status) {
        this.status = status;
    }

    @JsonIgnore
    public Prompt getPrompt() {
        return prompt;
    }

    public void setPrompt(Prompt prompt) {
        this.prompt = prompt;
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

    public String toText() {
        return DefaultAiChatExchangePersister.instance().serialize(this);
    }

    public boolean checkNotEmpty() {
        String content = getContent();
        if (StringHelper.isEmpty(content)) {
            invalidReason = new ErrorBean(ERR_AI_RESULT_IS_EMPTY.getErrorCode());
            setInvalid(true);
            return false;
        }
        return true;
    }

    /**
     * 检查结果的最后一行为指定内容，如果不是则抛出异常
     *
     * @param expected 期待返回的文本行
     */
    public boolean checkAndRemoveEndLine(String expected) {
        String content = getContent();
        if (StringHelper.isEmpty(content)) {
            LOG.debug("nop.err.ai.content-is-empty");

            invalidReason = new ErrorBean(ERR_AI_RESULT_IS_EMPTY.getErrorCode());
            setInvalid(true);
            return false;
        }
        int pos = content.lastIndexOf('\n');
        if (pos == content.length() - 1) {
            pos = content.lastIndexOf('\n', pos - 1);
        }
        if (pos < 0) {
            LOG.debug("nop.err.ai.missing-expected-line:{}", expected);

            invalidReason = new ErrorBean(ERR_AI_RESULT_INVALID_END_LINE.getErrorCode())
                    .param(ARG_EXPECTED, expected).param(ARG_LINE, "");
            setInvalid(true);
            return false;
        }

        String endLine = content.substring(pos + 1).trim();
        if (!endLine.equals(expected.trim())) {
            LOG.debug("nop.err.ai.end-line-not-match:expected={},endLine={}", expected, endLine);

            invalidReason = new ErrorBean(ERR_AI_RESULT_INVALID_END_LINE.getErrorCode())
                    .param(ARG_EXPECTED, expected)
                    .param(ARG_LINE, endLine);
            setInvalid(true);
            return false;
        }

        setContent(content.substring(0, pos));
        return true;
    }

    public String requireBlock(String blockBegin, String blockEnd) {
        return requireBlock(blockBegin, blockEnd, false);
    }

    public String requireBlock(String blockBegin, String blockEnd, boolean optionalBegin) {
        return getBlock(blockBegin, blockEnd, optionalBegin, false);
    }

    public String parseContentBlock(String blockBegin, String blockEnd, boolean optionalBegin, boolean optional) {
        String block = getBlock(blockBegin, blockEnd, optionalBegin, optional);
        if (block != null) {
            setContent(block);
        }
        return block;
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
                        .param(ARG_EXPECTED, blockBegin);
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
                    .param(ARG_EXPECTED, blockEnd);
            setInvalid(true);
            return null;
        }

        int pos2 = markPos2[0];
        return content.substring(pos, pos2);
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

    public MarkdownCodeBlock parseCodeBlock(String lang) {
        String content = getContent();
        if (StringHelper.isEmpty(content))
            return null;
        return CodeResponseParser.instance().parseResponse(content, lang);
    }

    public Object getResultValue() {
        return getOutput(AiCoreConstants.OUTPUT_VAR_RESULT);
    }

    public String getResultText() {
        return ConvertHelper.toString(getOutput(AiCoreConstants.OUTPUT_VAR_RESULT));
    }

    @Override
    public String toString() {
        return "AiChatResponse{" +
                "index=" + index +
                ", status=" + status +
                ", completionTokens=" + getCompletionTokens() +
                ", content='" + getContent() +
                '}';
    }
}
