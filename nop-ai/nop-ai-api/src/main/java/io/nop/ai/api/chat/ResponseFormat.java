/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.ai.api.chat;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.nop.api.core.annotations.data.DataBean;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 响应格式对象载体（plan 326）。把 {@link ChatOptions#getResponseFormat()} 从纯 {@code String}
 * 升级为可承载结构化 {@code json_schema} 的对象，作为 {@code ResponsesDialect}（plan 330）的前置。
 * <p>
 * 规范取值：{@link #TYPE_JSON_OBJECT}（仅约束返回 JSON）、{@link #TYPE_JSON_SCHEMA}（带 {@code schema}
 * 描述结构）。其余字符串值（如历史用法 {@code "json"}）作为 {@code type} 透传，保持向后兼容。
 */
@DataBean
public class ResponseFormat {

    public static final String TYPE_JSON_OBJECT = "json_object";

    public static final String TYPE_JSON_SCHEMA = "json_schema";

    /**
     * 格式类型。规范取值 {@link #TYPE_JSON_OBJECT} / {@link #TYPE_JSON_SCHEMA}；历史用法 {@code "json"} 等
     * 字符串亦作为 type 透传，由旧 {@link ChatOptions#getResponseFormat()} 字符串视图回放。
     */
    private String type;

    /**
     * JSON Schema 结构描述（仅 {@link #TYPE_JSON_SCHEMA} 时有意义，可空）。
     */
    private Map<String, Object> schema;

    public ResponseFormat() {
    }

    public ResponseFormat(String type) {
        this.type = type;
    }

    public ResponseFormat(String type, Map<String, Object> schema) {
        this.type = type;
        if (schema != null) {
            this.schema = new LinkedHashMap<>(schema);
        }
    }

    public static ResponseFormat jsonObject() {
        return new ResponseFormat(TYPE_JSON_OBJECT);
    }

    public static ResponseFormat jsonSchema(Map<String, Object> schema) {
        return new ResponseFormat(TYPE_JSON_SCHEMA, schema);
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public Map<String, Object> getSchema() {
        return schema;
    }

    public void setSchema(Map<String, Object> schema) {
        this.schema = schema;
    }

    public ResponseFormat copy() {
        ResponseFormat copy = new ResponseFormat();
        copy.type = this.type;
        if (this.schema != null) {
            copy.schema = new LinkedHashMap<>(this.schema);
        }
        return copy;
    }
}
