/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.ai.api.chat.messages;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.nop.api.core.annotations.data.DataBean;

import java.util.Map;

/**
 * 工具定义，用于描述可用的工具（函数）
 * 
 * OpenAI/Claude API 格式：
 * {
 *   "type": "function",
 *   "function": {
 *     "name": "get_weather",
 *     "description": "获取指定城市的天气",
 *     "parameters": {
 *       "type": "object",
 *       "properties": {
 *         "city": {"type": "string", "description": "城市名称"}
 *       },
 *       "required": ["city"]
 *     }
 *   }
 * }
 */
@DataBean
public class ChatToolDefinition {

    /**
     * 工具类型，默认为 "function"
     */
    private String type;

    /**
     * 工具名称
     */
    private String name;

    /**
     * 工具描述
     */
    private String description;

    /**
     * 参数定义（JSON Schema 格式）
     */
    private Map<String, Object> parameters;

    public ChatToolDefinition() {
        this.type = "function";
    }

    public ChatToolDefinition(String name, String description) {
        this();
        this.name = name;
        this.description = description;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public Map<String, Object> getParameters() {
        return parameters;
    }

    public void setParameters(Map<String, Object> parameters) {
        this.parameters = parameters;
    }

    /**
     * 创建工具定义的深拷贝
     */
    public ChatToolDefinition copy() {
        ChatToolDefinition copy = new ChatToolDefinition();
        copy.type = this.type;
        copy.name = this.name;
        copy.description = this.description;
        if (this.parameters != null) {
            copy.parameters = new java.util.LinkedHashMap<>(this.parameters);
        }
        return copy;
    }

    /**
     * 快速创建工具定义
     */
    public static ChatToolDefinition of(String name, String description) {
        return new ChatToolDefinition(name, description);
    }

    /**
     * 快速创建工具定义并设置参数
     */
    public static ChatToolDefinition of(String name, String description, Map<String, Object> parameters) {
        ChatToolDefinition def = new ChatToolDefinition(name, description);
        def.setParameters(parameters);
        return def;
    }
}
