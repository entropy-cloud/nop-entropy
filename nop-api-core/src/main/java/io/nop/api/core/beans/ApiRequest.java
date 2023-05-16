/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.api.core.beans;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.nop.api.core.annotations.data.DataBean;
import io.nop.api.core.rpc.IApiResponseNormalizer;

import java.util.LinkedHashMap;
import java.util.Map;

@DataBean
public final class ApiRequest<T> extends ApiMessage {
    private static final long serialVersionUID = -2652499046301680161L;

    /**
     * GraphQL所支持的返回结果过滤能力
     */
    private FieldSelectionBean fieldSelection;
    private T data;

    private IApiResponseNormalizer responseNormalizer;

    public static <T> ApiRequest<T> build(T data) {
        ApiRequest<T> request = new ApiRequest<>();
        request.setData(data);
        return request;
    }

    @JsonIgnore
    public IApiResponseNormalizer getResponseNormalizer() {
        return responseNormalizer;
    }

    public void setResponseNormalizer(IApiResponseNormalizer responseNormalizer) {
        this.responseNormalizer = responseNormalizer;
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public FieldSelectionBean getFieldSelection() {
        return fieldSelection;
    }

    public void setFieldSelection(FieldSelectionBean fieldSelection) {
        this.fieldSelection = fieldSelection;
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
                ret.setHeaders(new LinkedHashMap<>(headers));
            }
        }
        ret.setData(data);
        return ret;
    }
}