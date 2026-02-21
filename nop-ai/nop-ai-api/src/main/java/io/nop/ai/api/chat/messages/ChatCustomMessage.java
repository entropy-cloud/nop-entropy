/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.ai.api.chat.messages;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.nop.api.core.annotations.data.DataBean;

import java.util.HashMap;
import java.util.Map;

/**
 * 自定义消息，支持扩展角色和自定义属性
 * 用于支持非标准的消息类型或需要携带额外数据的场景
 */
@DataBean
public class ChatCustomMessage extends ChatMessage {

    /**
     * 消息内容
     */
    private String content;

    /**
     * 自定义角色名称
     */
    private String customRole;

    /**
     * 扩展属性，用于存储额外的自定义数据
     */
    private Map<String, Object> extensions;

    public ChatCustomMessage() {
    }

    public ChatCustomMessage(String customRole, String content) {
        this.customRole = customRole;
        this.content = content;
    }

    @Override
    public String getRole() {
        return customRole != null ? customRole : "custom";
    }

    /**
     * 设置自定义角色名称
     */
    public void setCustomRole(String customRole) {
        this.customRole = customRole;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public String getCustomRole() {
        return customRole;
    }

    @Override
    public String getContent() {
        return content;
    }

    @Override
    public void setContent(String content) {
        this.content = content;
    }

    @JsonAnyGetter
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public Map<String, Object> getExtensions() {
        return extensions;
    }

    public void setExtensions(Map<String, Object> extensions) {
        this.extensions = extensions;
    }

    @JsonAnySetter
    public void addExtension(String key, Object value) {
        if (this.extensions == null) {
            this.extensions = new HashMap<>();
        }
        this.extensions.put(key, value);
    }

    /**
     * 获取扩展属性值
     */
    public Object getExtension(String key) {
        return extensions != null ? extensions.get(key) : null;
    }

    /**
     * 获取扩展属性值并转换为指定类型
     */
    @SuppressWarnings("unchecked")
    public <T> T getExtension(String key, Class<T> type) {
        Object value = getExtension(key);
        if (value != null && type.isInstance(value)) {
            return (T) value;
        }
        return null;
    }

    @Override
    public ChatCustomMessage copy() {
        ChatCustomMessage copy = new ChatCustomMessage();
        copy.setMessageId(this.getMessageId());
        copy.customRole = this.customRole;
        copy.content = this.content;
        if (this.extensions != null) {
            copy.extensions = new HashMap<>(this.extensions);
        }
        return copy;
    }
}
