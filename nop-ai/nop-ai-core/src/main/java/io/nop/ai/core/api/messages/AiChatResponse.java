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
import io.nop.api.core.annotations.data.DataBean;
import io.nop.api.core.beans.ErrorBean;
import io.nop.commons.util.StringHelper;

import java.util.HashMap;
import java.util.Map;

import static io.nop.ai.core.AiCoreErrors.ARG_EXPECTED;
import static io.nop.ai.core.AiCoreErrors.ARG_LINE;
import static io.nop.ai.core.AiCoreErrors.ARG_NAME;
import static io.nop.ai.core.AiCoreErrors.ARG_VALUE;
import static io.nop.ai.core.AiCoreErrors.ERR_AI_RESULT_INVALID_END_LINE;
import static io.nop.ai.core.AiCoreErrors.ERR_AI_RESULT_INVALID_NUMBER;
import static io.nop.ai.core.AiCoreErrors.ERR_AI_RESULT_IS_EMPTY;
import static io.nop.ai.core.AiCoreErrors.ERR_AI_RESULT_NO_EXPECTED_PART;
import static io.nop.ai.core.commons.debug.DebugMessageHelper.collectDebugText;

@DataBean
public class AiChatResponse {

    private Prompt prompt;

    private Integer index;
    private MessageStatus status;

    private AiAssistantMessage message;

    private Integer promptTokens;
    private Integer completionTokens;
    private Integer totalTokens;

    private Map<String, Object> parsedValues;
    private Map<String, Object> attributes;

    private boolean invalid;
    private ErrorBean invalidReason;

    public AiChatResponse() {
        this.message = new AiAssistantMessage();
    }

    public AiChatResponse(AiAssistantMessage message) {
        this.message = message;
    }

    public String getContent() {
        return message.getContent();
    }

    public void setContent(String content) {
        this.message.setContent(content);
    }

    public boolean isEmpty() {
        return StringHelper.isEmpty(getContent()) && parsedValues == null;
    }

    public String getBlockFromPrompt(String blockBegin, String blockEnd) {
        if (prompt == null)
            return null;

        String message = prompt.getLastMessage().getContent();
        if (message == null)
            return null;

        int pos = message.indexOf(blockBegin);
        int pos2 = message.lastIndexOf(blockEnd);
        if (pos < 0 || pos2 < 0)
            return null;

        return message.substring(pos + blockBegin.length(), pos2);
    }

    public String getThink() {
        return message.getThink();
    }

    public void setThink(String think) {
        this.message.setThink(think);
    }

    public Integer getPromptTokens() {
        return promptTokens;
    }

    public void setPromptTokens(Integer promptTokens) {
        this.promptTokens = promptTokens;
    }

    public Integer getCompletionTokens() {
        return completionTokens;
    }

    public void setCompletionTokens(Integer completionTokens) {
        this.completionTokens = completionTokens;
    }

    public Integer getTotalTokens() {
        return totalTokens;
    }

    public void setTotalTokens(Integer totalTokens) {
        this.totalTokens = totalTokens;
    }

    public AiAssistantMessage getMessage() {
        return message;
    }

    public void setMessage(AiAssistantMessage message) {
        this.message = message;
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

    public Map<String, Object> getParsedValues() {
        return parsedValues;
    }

    public void setParsedValues(Map<String, Object> parsedValues) {
        this.parsedValues = parsedValues;
    }

    public Object getParsedValue(String name) {
        if (parsedValues == null)
            return null;
        return parsedValues.get(name);
    }

    public void setParsedValue(String name, Object value) {
        if (parsedValues == null)
            parsedValues = new HashMap<>();
        parsedValues.put(name, value);
    }

    @JsonIgnore
    public Map<String, Object> getAttributes() {
        return attributes;
    }

    public void setAttributes(Map<String, Object> attributes) {
        this.attributes = attributes;
    }

    public Object getAttribute(String name) {
        if (attributes == null)
            return null;
        return attributes.get(name);
    }

    public void setAttribute(String name, Object value) {
        if (attributes == null)
            attributes = new HashMap<>();
        attributes.put(name, value);
    }

    public String toDebugText() {
        StringBuilder sb = new StringBuilder();
        collectDebugText(sb, this);
        return sb.toString();
    }

    /**
     * 检查结果的最后一行为指定内容，如果不是则抛出异常
     *
     * @param expected 期待返回的文本行
     */
    public void checkAndRemoveEndLine(String expected) {
        String content = getContent();
        if (StringHelper.isEmpty(content)) {
            invalidReason = new ErrorBean(ERR_AI_RESULT_IS_EMPTY.getErrorCode());
            setInvalid(true);
            return;
        }
        int pos = content.lastIndexOf('\n');
        if (pos == content.length() - 1) {
            pos = content.lastIndexOf('\n', pos - 1);
        }
        if (pos < 0) {
            invalidReason = new ErrorBean(ERR_AI_RESULT_INVALID_END_LINE.getErrorCode())
                    .param(ARG_EXPECTED, expected).param(ARG_LINE, "");
            setInvalid(true);
            return;
        }

        String endLine = content.substring(pos + 1).trim();
        if (!endLine.equals(expected.trim())) {
            invalidReason = new ErrorBean(ERR_AI_RESULT_INVALID_END_LINE.getErrorCode())
                    .param(ARG_EXPECTED, expected)
                    .param(ARG_LINE, endLine);
            setInvalid(true);
            return;
        }

        setContent(content.substring(0, pos));
    }

    public String requireBlock(String blockBegin, String blockEnd) {
        return requireBlock(blockBegin, blockEnd, false);
    }

    public String requireBlock(String blockBegin, String blockEnd, boolean optionalBegin) {
        String content = getContent();
        if (StringHelper.isEmpty(content)) {
            invalidReason = new ErrorBean(ERR_AI_RESULT_IS_EMPTY.getErrorCode());
            setInvalid(true);
            return null;
        }

        int pos = content.indexOf(blockBegin);
        if (pos < 0) {
            if (!optionalBegin) {
                invalidReason = new ErrorBean(ERR_AI_RESULT_NO_EXPECTED_PART.getErrorCode())
                        .param(ARG_EXPECTED, blockBegin);
                setInvalid(true);
                return null;
            }
        }

        if (pos < 0) {
            pos = 0;
        } else {
            pos += blockBegin.length();
        }

        int pos2 = content.lastIndexOf(blockEnd);
        if (pos2 < 0) {
            invalidReason = new ErrorBean(ERR_AI_RESULT_NO_EXPECTED_PART.getErrorCode())
                    .param(ARG_EXPECTED, blockEnd);
            setInvalid(true);
            return null;
        }
        return content.substring(pos, pos2);
    }

    public Number requireNumberBlock(String name, String blockBegin, String blockEnd) {
        String block = requireBlock(blockBegin, blockEnd);
        if (block == null)
            return null;
        Number num = StringHelper.tryParseNumber(block);
        if (num == null) {
            setInvalid(true);
            invalidReason = new ErrorBean(ERR_AI_RESULT_INVALID_NUMBER.getErrorCode())
                    .param(ARG_NAME, name)
                    .param(ARG_VALUE, block);
            return null;
        }
        return num;
    }

    public Object parseBlock(String name, String blockBegin, String blockEnd) {
        Object value = requireBlock(blockBegin, blockEnd);
        if (value != null)
            setParsedValue(name, value);
        return value;
    }

    public Object parseNumberBlock(String name, String blockBegin, String blockEnd) {
        Number value = requireNumberBlock(name, blockBegin, blockEnd);
        if (value != null)
            setParsedValue(name, value);
        return value;
    }

    public String parseContentBlock(String blockBegin, String blockEnd, boolean optionalBegin) {
        String content = requireBlock(blockBegin, blockEnd, optionalBegin);
        if (content != null)
            setContent(content);
        return content;
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
