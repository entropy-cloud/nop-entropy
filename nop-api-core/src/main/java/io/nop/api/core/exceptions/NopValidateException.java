/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.api.core.exceptions;

import io.nop.api.core.beans.ErrorBean;

import java.util.List;

import static io.nop.api.core.ApiErrors.ARG_ERRORS;
import static io.nop.api.core.ApiErrors.ERR_VALIDATE_CHECK_FAIL;

public class NopValidateException extends NopException {
    private static final long serialVersionUID = 7086654708587272211L;

    public NopValidateException(ErrorCode errorCode) {
        super(errorCode);
    }

    public NopValidateException() {
        this(ERR_VALIDATE_CHECK_FAIL);
    }

    public List<ErrorBean> getErrors() {
        return (List<ErrorBean>) getParam(ARG_ERRORS);
    }
}
