/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.api.core.beans;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.nop.api.core.annotations.data.DataBean;
import io.nop.api.core.annotations.graphql.GraphQLObject;
import io.nop.api.core.annotations.meta.PropMeta;

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

    /**
     * content返回的资源文件在下载完毕后是否需要被自动清理
     */
    private final boolean autoCleanResource;

    public WebContentBean(@JsonProperty("contentType") String contentType,
                          @JsonProperty("content") Object content,
                          @JsonProperty("fileName") String fileName,
                          @JsonProperty("autoCleanResource") boolean autoCleanResource
    ) {
        this.contentType = contentType;
        this.content = content;
        this.fileName = fileName;
        this.autoCleanResource = autoCleanResource;
    }

    public WebContentBean(@JsonProperty("contentType") String contentType,
                          @JsonProperty("content") Object content,
                          @JsonProperty("fileName") String fileName
    ) {
        this(contentType, content, fileName, false);
    }

    public WebContentBean(@JsonProperty("contentType") String contentType,
                          @JsonProperty("content") Object content) {
        this(contentType, content, null, false);
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

    @PropMeta(propId = 1)
    public String getContentType() {
        return contentType;
    }

    @PropMeta(propId = 2)
    public Object getContent() {
        return content;
    }

    @PropMeta(propId = 3)
    public String getFileName() {
        return fileName;
    }

    @PropMeta(propId = 4)
    public boolean isAutoCleanResource() {
        return autoCleanResource;
    }
}