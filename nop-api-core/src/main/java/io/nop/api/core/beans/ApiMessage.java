/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.api.core.beans;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import io.nop.api.core.util.ApiHeaders;
import io.nop.api.core.util.ICloneable;

import java.io.Serializable;
import java.util.Map;
import java.util.TreeMap;

public abstract class ApiMessage implements Serializable, ICloneable {
    private static final long serialVersionUID = 1195025223699746417L;
    private Map<String, Object> headers;

    @JsonInclude(Include.NON_EMPTY)
    public Map<String, Object> getHeaders() {
        return headers;
    }

    public void setHeaders(Map<String, Object> headers) {
        this.headers = headers;
    }

    public Object getHeader(String name) {
        return ApiHeaders.getHeader(headers, name);
    }

    public void setHeader(String name, Object value) {
        if (headers == null)
            headers = new TreeMap<>();
        ApiHeaders.setHeader(headers, name, value);
    }

    public void removeHeader(String name) {
        if (headers != null) {
            headers.remove(name);
        }
    }

    public abstract Object getData();

    public abstract ApiMessage cloneInstance(boolean includeHeaders);
}