/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.exceptions;

import io.nop.api.core.exceptions.IErrorMessageManager;
import io.nop.api.core.exceptions.IException;
import io.nop.api.core.exceptions.NopException;

import java.util.Set;
import java.util.function.Predicate;

public class NamedExceptionFilter implements Predicate<Throwable> {
    private final IErrorMessageManager errorMessageManager;
    private Set<String> acceptClasses;
    private Set<String> acceptErrorCodes;
    private Set<String> rejectClasses;
    private Set<String> rejectErrorCodes;
    private boolean acceptError;
    private boolean acceptBizFatal;

    public NamedExceptionFilter(IErrorMessageManager errorMessageManager) {
        this.errorMessageManager = errorMessageManager;
    }

    public NamedExceptionFilter() {
        this(ErrorMessageManager.instance());
    }

    public void setAcceptBizFatal(boolean acceptBizFatal) {
        this.acceptBizFatal = acceptBizFatal;
    }

    public void setAcceptClasses(Set<String> acceptClasses) {
        this.acceptClasses = acceptClasses;
    }

    public void setAcceptErrorCodes(Set<String> acceptErrorCodes) {
        this.acceptErrorCodes = acceptErrorCodes;
    }

    public void setRejectClasses(Set<String> rejectClasses) {
        this.rejectClasses = rejectClasses;
    }

    public void setRejectErrorCodes(Set<String> rejectErrorCodes) {
        this.rejectErrorCodes = rejectErrorCodes;
    }

    public void setAcceptError(boolean acceptError) {
        this.acceptError = acceptError;
    }

    @Override
    public boolean test(Throwable throwable) {
        if (!acceptError && throwable instanceof Error)
            return false;

        if (!acceptBizFatal) {
            if (throwable instanceof NopException && ((NopException) throwable).isBizFatal())
                return false;
        }

        Throwable e = errorMessageManager.getRealCause(throwable);

        if (rejectClasses != null) {
            String className = e.getClass().getName();
            if (rejectClasses.contains(className))
                return false;
        }

        if (rejectErrorCodes != null) {
            if (e instanceof IException) {
                String errorCode = ((IException) e).getErrorCode();
                if (rejectErrorCodes.contains(errorCode))
                    return false;
            }
        }

        if (acceptClasses != null) {
            String className = e.getClass().getName();
            if (acceptClasses.contains(className))
                return true;
        }

        if (acceptErrorCodes != null) {
            if (e instanceof IException) {
                String errorCode = ((IException) e).getErrorCode();
                if (acceptErrorCodes.contains(errorCode))
                    return true;
            }
        }

        return false;
    }
}