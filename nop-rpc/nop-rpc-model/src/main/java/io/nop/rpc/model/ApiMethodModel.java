/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.rpc.model;

import io.nop.api.core.convert.ConvertHelper;
import io.nop.api.core.exceptions.NopException;
import io.nop.rpc.model._gen._ApiMethodModel;

import static io.nop.rpc.model.RpcModelConstants.OPTION_REST_PATH;
import static io.nop.rpc.model.RpcModelConstants.OPTION_TCC_CANCEL_METHOD;
import static io.nop.rpc.model.RpcModelConstants.OPTION_TCC_CONFIRM_METHOD;
import static io.nop.rpc.model.RpcModelConstants.OPTION_TIMEOUT;

public class ApiMethodModel extends _ApiMethodModel implements IWithOptions {
    public ApiMethodModel() {

    }

    public String getRestPath() {
        return (String) getOptionValue(OPTION_REST_PATH);
    }

    public void setRestPath(String restPath) {
        setOptionValue(OPTION_REST_PATH, restPath);
    }

    public String getTccConfirmMethod() {
        return (String) getOptionValue(OPTION_TCC_CONFIRM_METHOD);
    }

    public void setTccConfirmMethod(String confirmMethod) {
        setOptionValue(OPTION_TCC_CONFIRM_METHOD, confirmMethod);
    }

    public String getTccCancelMethod() {
        return (String) getOptionValue(OPTION_TCC_CANCEL_METHOD);
    }

    public void setTccCancelMethod(String cancelMethod) {
        setOptionValue(OPTION_TCC_CANCEL_METHOD, cancelMethod);
    }

    public Long getTimeout() {
        return ConvertHelper.toLong(getOptionValue(OPTION_TIMEOUT), NopException::new);
    }

    public void setTimeout(Long timeout) {
        setOptionValue(OPTION_TIMEOUT, timeout);
    }
}
