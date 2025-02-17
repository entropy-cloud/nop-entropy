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

import java.util.HashMap;
import java.util.Map;

@DataBean
public class AiResultMessage extends AbstractTextMessage {
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

    public boolean isInvalid() {
        return invalid;
    }

    public void setInvalid(boolean invalid) {
        this.invalid = invalid;
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
