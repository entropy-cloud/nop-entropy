/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.api.core.exceptions;

import io.nop.api.core.beans.ErrorBean;
import io.nop.api.core.util.ApiStringHelper;
import io.nop.api.core.util.SourceLocation;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static io.nop.api.core.ApiErrors.ARG_ERRORS;
import static io.nop.api.core.ApiErrors.ERR_VALIDATE_CHECK_FAIL;

public class NopValidateException extends NopException {
    private static final long serialVersionUID = 7086654708587272211L;

    private List<ErrorBean> errors;

    public NopValidateException(ErrorCode errorCode) {
        super(errorCode);
    }

    public NopValidateException() {
        this(ERR_VALIDATE_CHECK_FAIL);
    }

    public NopValidateException errors(List<ErrorBean> errors) {
        this.errors = errors;

        Set<String> messages = new LinkedHashSet<>();
        String loc = null;
        for (ErrorBean error : errors) {
            if (loc == null && error.getSourceLocation() != null)
                loc = error.getSourceLocation();

            String desc = error.getDescription();
            if (ApiStringHelper.isEmpty(desc)) {
                desc = error.getErrorCode();
            }
            messages.add(desc);
        }
        param(ARG_ERRORS, ApiStringHelper.join(messages, "\n"));

        if (this.getErrorLocation() == null && loc != null)
            this.loc(SourceLocation.parse(loc));

        return this;
    }

    public List<ErrorBean> getErrors() {
        return errors;
    }
}
