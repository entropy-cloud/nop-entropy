/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.api.core.beans;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.nop.api.core.annotations.data.DataBean;
import io.nop.api.core.annotations.graphql.GraphQLObject;

@GraphQLObject
@DataBean
public class WebContentBean {
    public static final String CONTENT_TYPE_OCTET = "application/octet-stream";
    public static final String CONTENT_TYPE_HTML = "text/html";
    public static final String CONTENT_TYPE_XML = "text/xml";
    public static final String CONTENT_TYPE_JAVASCRIPT = "text/javascript";
    public static final String CONTENT_TYPE_JSON = "application/json";
    public static final String CONTENT_TYPE_TEXT = "text/plain";

    private final String contentType;
    private final Object content;
    private final String fileName;

    public WebContentBean(@JsonProperty("contentType") String contentType,
                          @JsonProperty("content") Object content,
                          @JsonProperty("fileName") String fileName) {
        this.contentType = contentType;
        this.content = content;
        this.fileName = fileName;
    }

    public WebContentBean(@JsonProperty("contentType") String contentType,
                          @JsonProperty("content") Object content) {
        this(contentType, content, null);
    }


    public static WebContentBean json(Object json) {
        return new WebContentBean(CONTENT_TYPE_JSON, json);
    }

    public static WebContentBean xml(String xml) {
        return new WebContentBean(CONTENT_TYPE_XML, xml);
    }

    public static WebContentBean html(String html) {
        return new WebContentBean(CONTENT_TYPE_HTML, html);
    }

    public static WebContentBean js(String js) {
        return new WebContentBean(CONTENT_TYPE_JAVASCRIPT, js);
    }

    public static WebContentBean binary(byte[] bytes) {
        return new WebContentBean(CONTENT_TYPE_OCTET, bytes);
    }

    public static WebContentBean text(String text) {
        return new WebContentBean(CONTENT_TYPE_TEXT, text);
    }

    public String getContentType() {
        return contentType;
    }

    public Object getContent() {
        return content;
    }

    public String getFileName() {
        return fileName;
    }
}