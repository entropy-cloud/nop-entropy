/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.http.api.client;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.nop.api.core.annotations.data.DataBean;
import io.nop.api.core.util.ApiStringHelper;
import io.nop.http.api.HttpApiConstants;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@DataBean
public class HttpRequest {

    private String url;
    private String method;
    private Map<String, Object> headers;
    private Map<String, Object> params;
    private Object body;
    private long timeout;
    private String dataType;

    /**
     * 一些interceptor可能会使用的扩展属性配置
     */
    private Map<String, Object> attrs;

    public static HttpRequest get(String url) {
        HttpRequest request = new HttpRequest();
        request.setUrl(url);
        request.setMethod(HttpApiConstants.METHOD_GET);
        return request;
    }

    public static HttpRequest post(String url) {
        HttpRequest request = new HttpRequest();
        request.setUrl(url);
        request.setMethod(HttpApiConstants.METHOD_POST);
        return request;
    }

    public String getUrlNoQuery() {
        String url = this.url;
        if (url == null)
            return url;
        int pos = url.indexOf('?');
        if (pos < 0)
            return url;
        return url.substring(0, pos);
    }

    public Map<String, Object> getParams() {
        return params;
    }

    public void setParams(Map<String, Object> params) {
        this.params = params;
    }

    public HttpRequest bearerToken(String token) {
        String value = "Bearer " + token;
        return header(HttpApiConstants.HEADER_AUTHORIZATION, value);
    }

    @JsonIgnore
    public boolean isFormData() {
        return HttpApiConstants.DATA_TYPE_FORM.equals(getDataType());
    }

    public String getDataType() {
        return dataType;
    }

    public void setDataType(String dataType) {
        this.dataType = dataType;
    }

    public HttpRequest dataType(String dataType) {
        this.dataType = dataType;
        return this;
    }

    public HttpRequest url(String url) {
        this.url = url;
        return this;
    }

    public HttpRequest param(String name, Object value) {
        if (params == null) {
            params = new LinkedHashMap<>();
        }
        params.put(name, value);
        return this;
    }

    public HttpRequest body(Object body) {
        this.body = body;
        return this;
    }

    public HttpRequest timeout(long timeout) {
        this.timeout = timeout;
        return this;
    }

    public HttpRequest header(String name, Object value) {
        if (headers == null) {
            headers = new LinkedHashMap<>();
        }
        headers.put(name, value);
        return this;
    }

    public long getTimeout() {
        return timeout;
    }

    public void setTimeout(long timeout) {
        this.timeout = timeout;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getMethod() {
        return method;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    public Map<String, Object> getHeaders() {
        return headers;
    }

    public void setHeaders(Map<String, Object> headers) {
        this.headers = headers;
    }

    public String getBearerToken() {
        String auth = getHeader(HttpApiConstants.HEADER_AUTHORIZATION);
        if (auth == null)
            return null;
        if (auth.startsWith(HttpApiConstants.BEARER_TOKEN_PREFIX))
            return auth.substring(HttpApiConstants.BEARER_TOKEN_PREFIX.length());
        return null;
    }

    public void setBearerToken(String token) {
        if (ApiStringHelper.isEmpty(token)) {
            removeHeader(HttpApiConstants.HEADER_AUTHORIZATION);
        } else {
            header(HttpApiConstants.HEADER_AUTHORIZATION, HttpApiConstants.BEARER_TOKEN_PREFIX + token);
        }
    }

    public String getHeader(String name) {
        if (headers == null)
            return null;
        Object value = headers.get(name);
        if (value == null)
            return null;
        if (value instanceof List) {
            List<Object> list = (List<Object>) value;
            if (list.isEmpty())
                return null;
            return String.valueOf(list.get(0));
        }
        return String.valueOf(value);
    }

    public void removeHeader(String name) {
        if (headers != null)
            headers.remove(name);
    }

    public Object getBody() {
        return body;
    }

    public void setBody(Object body) {
        this.body = body;
    }

    public Object getAttr(String name) {
        return attrs == null ? null : attrs.get(name);
    }

    public void setAttr(String name, Object value) {
        if (attrs == null)
            attrs = new HashMap<>();
        attrs.put(name, value);
    }

    @JsonIgnore
    public Map<String, Object> getAttrs() {
        return attrs;
    }

    public void setAttrs(Map<String, Object> attrs) {
        this.attrs = attrs;
    }
}