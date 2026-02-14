/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.ai.api.chat.messages;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.nop.api.core.annotations.data.DataBean;
import io.nop.api.core.json.JSON;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 工具调用信息
 */
@DataBean
public class ChatToolCall {

    /**
     * 工具ID
     */
    @JsonProperty("id")
    private String id;

    /**
     * 函数名称
     */
    @JsonProperty("name")
    private String name;

    /**
     * 函数参数（JSON字符串）
     */
    @JsonProperty("arguments")
    private Map<String, Object> arguments;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Map<String, Object> getArguments() {
        return arguments;
    }

    public void setArguments(Map<String, Object> arguments) {
        this.arguments = arguments;
    }

    @JsonIgnore
    public String getArgumentsText() {
        if (arguments == null)
            return null;
        return JSON.stringify(arguments);
    }

    /**
     * 创建工具调用的深拷贝
     */
    public ChatToolCall copy() {
        ChatToolCall copy = new ChatToolCall();
        copy.id = this.id;
        copy.name = this.name;
        if (this.arguments != null) {
            copy.arguments = new LinkedHashMap<>(this.arguments);
        }
        return copy;
    }
}
