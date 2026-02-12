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
}