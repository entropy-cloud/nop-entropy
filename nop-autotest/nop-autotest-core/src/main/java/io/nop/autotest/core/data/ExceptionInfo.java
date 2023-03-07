/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.autotest.core.data;

import io.nop.api.core.annotations.data.DataBean;
import io.nop.api.core.exceptions.IException;
import io.nop.commons.util.CollectionHelper;

import java.util.Map;

/**
 * 将Throwable类型的对象转化为json格式保存时所使用的帮助类
 */
@DataBean
public class ExceptionInfo {
    private String className;
    private String errorCode;
    private Map<String, String> params;
    private ExceptionInfo cause;

    public static ExceptionInfo buildFrom(Throwable e) {
        String className = e.getClass().getName();
        String errorCode = null;
        Map<String, String> params = null;
        if (e instanceof IException) {
            IException error = (IException) e;
            errorCode = error.getErrorCode();
            params = CollectionHelper.toStringMap(error.getParams());
        }

        ExceptionInfo data = new ExceptionInfo();
        data.setClassName(className);
        data.setErrorCode(errorCode);
        data.setParams(params);

        Throwable cause = e.getCause();
        if (cause != null) {
            data.setCause(buildFrom(cause));
        }

        return data;
    }

    public String getClassName() {
        return className;
    }

    public void setClassName(String className) {
        this.className = className;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public void setErrorCode(String errorCode) {
        this.errorCode = errorCode;
    }

    public Map<String, String> getParams() {
        return params;
    }

    public void setParams(Map<String, String> params) {
        this.params = params;
    }

    public ExceptionInfo getCause() {
        return cause;
    }

    public void setCause(ExceptionInfo cause) {
        this.cause = cause;
    }
}