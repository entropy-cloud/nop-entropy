/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.core.exceptions;

import io.nop.api.core.beans.ErrorBean;

/**
 * 可以通过此接口定制从异常对象解析得到异常信息的过程。 缺省情况下ErrorMessageManager使用defaultBuildErrorMessage函数来处理。
 */
public interface IErrorMessageBuilder {
    ErrorBean buildErrorMessage(String locale, Throwable e, boolean includeStack);
}