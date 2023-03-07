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
import io.nop.api.core.ApiConstants;
import io.nop.api.core.annotations.data.DataBean;
import io.nop.api.core.util.ICloneable;
import io.nop.api.core.util.SourceLocation;
import io.nop.api.core.validate.IValidationErrorCollector;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 根据NopException转化得到的错误信息对象
 */
@DataBean
public class ErrorBean implements Serializable, Comparable<ErrorBean>, ICloneable {
    private static final long serialVersionUID = -7401450479448631186L;

    private String sourceLocation;

    private int status = ApiConstants.API_STATUS_FAIL;
    private String errorCode;
    private String description;
    private Map<String, Object> params;
    private boolean bizFatal;

    private String errorStack;

    private int severity;

    private Map<String, ErrorBean> details;

    private ErrorBean cause;

    private boolean forPublic;

    public ErrorBean() {
    }

    public ErrorBean(String errorCode) {
        this.errorCode = errorCode;
    }

    @Override
    public ErrorBean cloneInstance() {
        ErrorBean ret = new ErrorBean();
        ret.setSourceLocation(sourceLocation);
        ret.setStatus(status);
        ret.setErrorCode(errorCode);
        ret.setDescription(description);
        ret.setParams(params);
        ret.setBizFatal(bizFatal);
        ret.setErrorStack(errorStack);
        ret.setSeverity(severity);
        ret.setDetails(details == null ? null : new LinkedHashMap<>(details));
        ret.setCause(cause);

        return ret;
    }

    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("ErrorBean[errorCode=").append(errorCode);
        sb.append(",params=").append(params);
        if (description != null) {
            sb.append(",description=").append(description);
        }
        sb.append("]");
        return sb.toString();
    }

    public int getStatus() {
        return status;
    }

    public void setStatus(int status) {
        this.status = status;
    }

    public Object getParam(String name) {
        if (params == null)
            return null;
        return params.get(name);
    }

    public boolean isForPublic() {
        return forPublic;
    }

    public void setForPublic(boolean forPublic) {
        this.forPublic = forPublic;
    }

    public boolean isBizFatal() {
        return bizFatal;
    }

    public void setBizFatal(boolean bizFatal) {
        this.bizFatal = bizFatal;
    }

    @Override
    public int compareTo(ErrorBean o) {
        return Integer.compare(-getSeverity(), -o.getSeverity());
    }

    public String getErrorCode() {
        return errorCode;
    }

    public void setErrorCode(String errorCode) {
        this.errorCode = errorCode;
    }

    @JsonInclude(Include.NON_EMPTY)
    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    @JsonInclude(Include.NON_EMPTY)
    public Map<String, Object> getParams() {
        return params;
    }

    public void setParams(Map<String, Object> params) {
        this.params = params;
    }

    public boolean hasParam() {
        return params != null && params.size() > 0;
    }

    @JsonInclude(Include.NON_EMPTY)
    public String getErrorStack() {
        return errorStack;
    }

    public void setErrorStack(String errorStack) {
        this.errorStack = errorStack;
    }

    public ErrorBean description(String description) {
        this.description = description;
        return this;
    }

    public ErrorBean param(String name, Object value) {
        if (this.params == null)
            this.params = new LinkedHashMap<>();
        this.params.put(name, value);
        return this;
    }

    @JsonInclude(Include.NON_EMPTY)
    public String getSourceLocation() {
        return sourceLocation;
    }

    public void setSourceLocation(String sourceLocation) {
        this.sourceLocation = sourceLocation;
    }

    public ErrorBean loc(String loc) {
        this.sourceLocation = loc;
        return this;
    }

    public ErrorBean loc(SourceLocation loc) {
        if (loc != null) {
            this.sourceLocation = loc.toString();
        }
        return this;
    }

    public ErrorBean errorStack(String stacktrace) {
        this.errorStack = stacktrace;
        return this;
    }

    public ErrorBean severity(int severity) {
        this.severity = severity;
        return this;
    }

    /**
     * 错误的严重级别。例如可能需要将错误排序，只对外返回严重级别最高的错误信息
     */
    @JsonInclude(Include.NON_DEFAULT)
    public int getSeverity() {
        return severity;
    }

    public void setSeverity(int severity) {
        this.severity = severity;
    }

    @JsonInclude(Include.NON_EMPTY)
    public Map<String, ErrorBean> getDetails() {
        return details;
    }

    public void setDetails(Map<String, ErrorBean> details) {
        this.details = details;
    }


    @JsonInclude(Include.NON_NULL)
    public ErrorBean getCause() {
        return cause;
    }

    public void setCause(ErrorBean cause) {
        this.cause = cause;
    }

    public void addToCollector(IValidationErrorCollector collector) {
        collector.addError(this);
    }
}