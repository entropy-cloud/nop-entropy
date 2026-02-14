/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.ai.api.chat.stream;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.nop.api.core.annotations.data.DataBean;

/**
 * 工具调用增量数据块
 * 
 * 用于流式传输中逐步接收工具调用信息：
 * - 第一个 chunk：id + name + 空 arguments
 * - 后续 chunks：逐步填充 arguments
 */
@DataBean
public class ChatToolCallChunk {

    /**
     * 工具调用索引（支持多工具调用时的顺序）
     */
    private Integer index;

    /**
     * 工具调用ID
     */
    private String id;

    /**
     * 工具类型（通常是 "function"）
     */
    private String type;

    /**
     * 函数名称（增量，可能分多次传输）
     */
    private String name;

    /**
     * 参数增量（JSON字符串片段，逐步累积）
     */
    private String arguments;

    public ChatToolCallChunk() {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public Integer getIndex() {
        return index;
    }

    public void setIndex(Integer index) {
        this.index = index;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
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
    public String getArguments() {
        return arguments;
    }

    public void setArguments(String arguments) {
        this.arguments = arguments;
    }

    /**
     * 检查是否有内容（id 或 name 或 arguments）
     */
    public boolean hasContent() {
        return id != null || name != null || (arguments != null && !arguments.isEmpty());
    }

    @Override
    public String toString() {
        return "ChatToolCallChunk{" +
                "index=" + index +
                ", id='" + id + '\'' +
                ", type='" + type + '\'' +
                ", name='" + name + '\'' +
                ", arguments='" + arguments + '\'' +
                '}';
    }
}
