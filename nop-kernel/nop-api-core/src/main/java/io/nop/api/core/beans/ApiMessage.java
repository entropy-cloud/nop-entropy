/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.api.core.beans;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import io.nop.api.core.ApiConstants;
import io.nop.api.core.util.ApiHeaders;
import io.nop.api.core.util.ApiStringHelper;
import io.nop.api.core.util.ICloneable;

import java.io.Serializable;
import java.util.Collection;
import java.util.Map;
import java.util.TreeMap;

public abstract class ApiMessage implements Serializable, ICloneable {
    private static final long serialVersionUID = 1195025223699746417L;
    private Map<String, Object> headers;

    @JsonInclude(Include.NON_EMPTY)
    public Map<String, Object> getHeaders() {
        if (headers == null)
            headers = new TreeMap<>();
        return headers;
    }

    @JsonIgnore
    public Map<String, Object> getHeadersOrNull() {
        return headers;
    }

    public Map<String, Object> copyHeaders() {
        if (headers == null)
            return null;
        return new TreeMap<>(headers);
    }

    public void setHeaders(Map<String, Object> headers) {
        this.headers = headers;
    }

    public Object getHeader(String name) {
        return ApiHeaders.getHeader(headers, name);
    }

    public boolean hasHeaders() {
        return headers != null && !headers.isEmpty();
    }

    public boolean hasHeader(String name) {
        return headers != null && headers.containsKey(name);
    }

    public void setHeader(String name, Object value) {
        if (headers == null)
            headers = new TreeMap<>();
        ApiHeaders.setHeader(headers, name, value);
    }

    public void addHeaders(Map<String, Object> headers) {
        if (headers != null) {
            headers.forEach(this::setHeader);
        }
    }

    public void addHeadersIfAbsent(Map<String, Object> headers) {
        if (headers == null || headers.isEmpty())
            return;

        if (this.headers == null) {
            this.headers = new TreeMap<>(headers);
        } else {
            headers.forEach((k, v) -> {
                if (!hasHeader(k)) {
                    setHeader(k, v);
                }
            });
        }
    }

    public Map<String, Object> getSelectedHeaders(Collection<String> headerNames) {
        return ApiHeaders.getHeaders(headers, headerNames);
    }

    public void removeHeader(String name) {
        if (headers != null) {
            headers.remove(name);
        }
    }

    @JsonIgnore
    public String getBearerToken() {
        String auth = ApiHeaders.getAuthorization(this);
        if (ApiStringHelper.isEmpty(auth)) {
            return ApiHeaders.getAuthToken(this);
        }

        if (ApiStringHelper.startsWithIgnoreCase(auth, ApiConstants.BEARER_TOKEN_PREFIX)) {
            return auth.substring(ApiConstants.BEARER_TOKEN_PREFIX.length()).trim();
        }
        return auth;
    }

    public void setBearerToken(String token) {
        if (ApiStringHelper.isEmpty(token)) {
            setHeader(ApiConstants.HEADER_AUTHORIZATION, null);
        } else {
            setHeader(ApiConstants.HEADER_AUTHORIZATION, ApiConstants.BEARER_TOKEN_PREFIX + token);
        }
    }

    public abstract Object getData();

    public abstract ApiMessage cloneInstance(boolean includeHeaders);

    // 默认 toString() 只输出对象 hash，错误/调试信息完全不可见。子类应重写 toString() 暴露核心字段，
    // 并通过此方法统一附加 headers，避免散落的拼接逻辑。

    /**
     * 将 headers 以 ",headers={k=v,...}" 形式追加到 sb。仅在存在 header 时追加，避免无 header 时产生噪音。
     */
    protected void appendHeaders(StringBuilder sb) {
        if (hasHeaders()) {
            sb.append(",headers=").append(getHeadersOrNull());
        }
    }

    /**
     * 对可能很大的字段（如 data）做定长截断，避免日志爆炸。超长部分以 "..." 标记。
     */
    protected static String truncate(Object value, int maxLen) {
        if (value == null)
            return null;
        String s = String.valueOf(value);
        if (s.length() <= maxLen)
            return s;
        return s.substring(0, maxLen) + "...(" + s.length() + ")";
    }
}