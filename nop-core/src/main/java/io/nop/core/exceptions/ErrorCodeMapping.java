/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.core.exceptions;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.nop.api.core.annotations.data.DataBean;
import io.nop.api.core.util.ISourceLocationGetter;
import io.nop.api.core.util.ISourceLocationSetter;
import io.nop.api.core.util.SourceLocation;

import java.io.Serializable;
import java.util.Map;

@DataBean
public class ErrorCodeMapping implements Serializable, ISourceLocationSetter, ISourceLocationGetter {
    private static final long serialVersionUID = 2239711477726306985L;

    private SourceLocation location;

    /**
     * 将内部异常码映射到外部异常码
     */
    private String mapToCode;
    private boolean returnParams;
    private Boolean bizFatal;
    private String messageKey;

    /**
     * 是否将导致本次异常的原始异常信息也作为description的一部分返回
     */
    private boolean includeCause;
    private boolean internal;
    private Integer status;

    /**
     * 根据异常对象来构造ApiResponse对象时，可以指定ApiResponse的http状态玛
     */
    private int httpStatus;
    private Map<String, String> mapToParams;

    public boolean isInternal() {
        return internal;
    }

    public void setInternal(boolean internal) {
        this.internal = internal;
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public String getMessageKey() {
        return messageKey;
    }

    public void setMessageKey(String messageKey) {
        this.messageKey = messageKey;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public boolean isIncludeCause() {
        return includeCause;
    }

    public void setIncludeCause(boolean includeCause) {
        this.includeCause = includeCause;
    }

    @Override
    public SourceLocation getLocation() {
        return location;
    }

    @Override
    public void setLocation(SourceLocation location) {
        this.location = location;
    }

    public Boolean getBizFatal() {
        return bizFatal;
    }

    public void setBizFatal(Boolean bizFatal) {
        this.bizFatal = bizFatal;
    }

    public String getMapToCode() {
        return mapToCode;
    }

    public void setMapToCode(String mapToCode) {
        this.mapToCode = mapToCode;
    }

    public int getHttpStatus() {
        return httpStatus;
    }

    public void setHttpStatus(int httpStatus) {
        this.httpStatus = httpStatus;
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public Map<String, String> getMapToParams() {
        return mapToParams;
    }

    public void setMapToParams(Map<String, String> mapToParams) {
        this.mapToParams = mapToParams;
    }

    public boolean isReturnParams() {
        return returnParams;
    }

    public void setReturnParams(boolean returnParams) {
        this.returnParams = returnParams;
    }
}