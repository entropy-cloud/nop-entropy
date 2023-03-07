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
    public static String CONTENT_TYPE_OCTET = "application/octet-stream";
    public static String CONTENT_TYPE_HTML = "text/html";
    public static String CONTENT_TYPE_XML = "text/xml";
    public static String CONTENT_TYPE_JAVASCRIPT = "text/javascript";
    public static String CONTENT_TYPE_JSON = "application/json";
    public static String CONTENt_TYPE_TEXT = "text/plain";

    private final String contentType;
    private final Object content;

    public WebContentBean(@JsonProperty("contentType") String contentType,
                          @JsonProperty("content") Object content) {
        this.contentType = contentType;
        this.content = content;
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
        return new WebContentBean(CONTENt_TYPE_TEXT, text);
    }

    public String getContentType() {
        return contentType;
    }

    public Object getContent() {
        return content;
    }
}