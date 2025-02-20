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
import io.nop.api.core.annotations.data.DataBean;
import io.nop.api.core.beans.ErrorBean;
import io.nop.api.core.exceptions.NopException;
import io.nop.commons.util.StringHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

import static io.nop.ai.core.AiCoreErrors.ARG_EXPECTED;
import static io.nop.ai.core.AiCoreErrors.ARG_LINE;
import static io.nop.ai.core.AiCoreErrors.ERR_AI_RESULT_INVALID_END_LINE;
import static io.nop.ai.core.AiCoreErrors.ERR_AI_RESULT_IS_EMPTY;
import static io.nop.ai.core.commons.debug.DebugMessageHelper.collectDebugText;

@DataBean
public class AiResultMessage extends AbstractTextMessage {
    static final Logger LOG = LoggerFactory.getLogger(AiResultMessage.class);

    private Prompt prompt;

    private Integer index;
    private MessageStatus status;
    private Integer promptTokens;
    private Integer completionTokens;
    private Integer totalTokens;
    private String think;

    private Object parseData;
    private Map<String, Object> attributes;

    private boolean invalid;
    private ErrorBean invalidReason;

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

    @Override
    public String getRole() {
        return AiCoreConstants.ROLE_ASSISTANT;
    }

    @JsonIgnore
    public Prompt getPrompt() {
        return prompt;
    }

    public void setPrompt(Prompt prompt) {
        this.prompt = prompt;
    }

    public String getThink() {
        return think;
    }

    public void setThink(String think) {
        this.think = think;
    }

    @JsonIgnore
    public Object getParseData() {
        return parseData;
    }

    public void setParseData(Object parseData) {
        this.parseData = parseData;
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
        if (StringHelper.isEmpty(content))
            throw new NopException(ERR_AI_RESULT_IS_EMPTY);
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

    @Override
    public String toString() {
        return "AiResultMessage{" +
                "index=" + index +
                ", status=" + status +
                ", totalTokens=" + totalTokens +
                ", content='" + getContent() + '\'' +
                ", metadataMap=" + metadataMap +
                '}';
    }
}
