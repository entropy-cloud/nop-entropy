/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.api.core.beans;

import io.nop.api.core.annotations.data.DataBean;
import io.nop.api.core.annotations.meta.PropMeta;

import java.io.Serializable;
import java.util.List;

@DataBean
public class CheckResultBean implements Serializable {
    private boolean success;
    private String errorCode;
    private String description;

    private List<ErrorBean> details;

    @PropMeta(propId = 1)
    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    @PropMeta(propId = 2)
    public String getErrorCode() {
        return errorCode;
    }

    public void setErrorCode(String errorCode) {
        this.errorCode = errorCode;
    }

    @PropMeta(propId = 3)
    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    @PropMeta(propId = 4)
    public List<ErrorBean> getDetails() {
        return details;
    }

    public void setDetails(List<ErrorBean> details) {
        this.details = details;
    }
}
