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
import io.nop.api.core.annotations.data.DataBean;
import io.nop.api.core.convert.ConvertHelper;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.ApiStringHelper;

import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

@DataBean
public final class ApiRequest<T> extends ApiMessage {
    private static final long serialVersionUID = -2652499046301680161L;

    /**
     * GraphQL所支持的返回结果过滤能力
     */
    private FieldSelectionBean selection;
    private T data;

    private Map<String, Object> properties;

    public static <T> ApiRequest<T> build(T data) {
        ApiRequest<T> request = new ApiRequest<>();
        request.setData(data);
        return request;
    }

    @JsonIgnore
    public Map<String, Object> getProperties() {
        return properties;
    }

    public Object getProperty(String name) {
        if (properties == null)
            return null;
        return properties.get(name);
    }

    public String getStringProperty(String name) {
        return ConvertHelper.toString(getProperty(name));
    }

    public int getIntProperty(String name, int defaultValue) {
        return ConvertHelper.toPrimitiveInt(getProperty(name), defaultValue, NopException::new);
    }

    public Integer getIntProperty(String name) {
        return ConvertHelper.toInt(getProperty(name));
    }

    public Boolean getBooleanProperty(String name) {
        return ConvertHelper.toBoolean(getProperty(name));
    }

    public void setProperty(String name, Object value) {
        if (ApiStringHelper.isEmptyObject(value)) {
            removeProperty(name);
        } else {
            if (properties == null)
                properties = new HashMap<>();
            properties.put(name, value);
        }
    }

    public void removeProperty(String name) {
        if (properties != null)
            properties.remove(name);
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public FieldSelectionBean getSelection() {
        return selection;
    }

    public void setSelection(FieldSelectionBean selection) {
        this.selection = selection;
    }

    public T getData() {
        return data;
    }

    public void setData(T data) {
        this.data = data;
    }

    @Override
    public ApiRequest<T> cloneInstance() {
        return cloneInstance(true);
    }

    @Override
    public ApiRequest<T> cloneInstance(boolean includeHeaders) {
        ApiRequest<T> ret = new ApiRequest<>();
        if (includeHeaders) {
            Map<String, Object> headers = getHeaders();
            if (headers != null) {
                ret.setHeaders(new TreeMap<>(headers));
            }
        }
        ret.setSelection(selection);
        ret.setData(data);
        return ret;
    }
}