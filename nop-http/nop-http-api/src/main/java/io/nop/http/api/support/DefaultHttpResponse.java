/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.http.api.support;

import io.nop.api.core.exceptions.NopException;
import io.nop.http.api.client.IHttpResponse;

import java.nio.charset.StandardCharsets;
import java.util.Map;

public class DefaultHttpResponse implements IHttpResponse {
    private int httpStatus;
    private Map<String, String> headers;
    private String charset;
    private String contentType;
    private byte[] bodyAsBytes;
    private String bodyAsText;

    public void setHttpStatus(int httpStatus) {
        this.httpStatus = httpStatus;
    }

    @Override
    public String getCharset() {
        return charset;
    }

    public void setCharset(String charset) {
        this.charset = charset;
    }

    public void setHeaders(Map<String, String> headers) {
        this.headers = headers;
    }

    @Override
    public int getHttpStatus() {
        return httpStatus;
    }

    @Override
    public Map<String, String> getHeaders() {
        return headers;
    }

    @Override
    public String getHeader(String name) {
        if (headers == null)
            return null;
        return headers.get(name);
    }

    @Override
    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    @Override
    public byte[] getBodyAsBytes() {
        if (bodyAsBytes != null) {
            return bodyAsBytes;
        }
        if (bodyAsText != null) {
            try {
                bodyAsBytes = bodyAsText.getBytes(charset != null ? charset : StandardCharsets.US_ASCII.name());
            } catch (Exception e) {
                throw NopException.adapt(e);
            }
        }
        return bodyAsBytes;
    }

    public void setBodyAsBytes(byte[] bodyAsBytes) {
        this.bodyAsBytes = bodyAsBytes;
    }

    public String getBodyAsText() {
        if (bodyAsText != null)
            return bodyAsText;

        if (bodyAsBytes != null) {
            try {
                bodyAsText = new String(bodyAsBytes, charset != null ? charset : StandardCharsets.US_ASCII.name());
            } catch (Exception e) {
                throw NopException.adapt(e);
            }
        }
        return bodyAsText;
    }

    public void setBodyAsText(String bodyAsText) {
        this.bodyAsText = bodyAsText;
    }
}