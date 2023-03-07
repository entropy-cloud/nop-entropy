/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.batch.core.common;

import io.nop.api.core.convert.ConvertHelper;
import io.nop.api.core.exceptions.NopException;
import io.nop.batch.core.IBatchTaskContext;

import static io.nop.batch.core.BatchErrors.ARG_VAR_NAME;
import static io.nop.batch.core.BatchErrors.ERR_BATCH_PERSIST_VAR_CONVERT_TYPE_FAIL;

public class AbstractBatchHandler {
    private String name;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPersistVarName(String varName) {
        if (this.name == null)
            return varName;
        return name + '.' + varName;
    }

    public Object getPersistVar(IBatchTaskContext context, String varName) {
        return context.getPersistVar(getPersistVarName(varName));
    }

    public void setPersistVar(IBatchTaskContext context, String varName, Object value) {
        context.setPersistVar(getPersistVarName(varName), value);
    }

    public String getPersistString(IBatchTaskContext context, String varName) {
        return ConvertHelper.toString(getPersistVar(context, varName), (String) null);
    }

    public Long getPersistLong(IBatchTaskContext context, String varName) {
        return ConvertHelper.toLong(getPersistVar(context, varName),
                err -> new NopException(ERR_BATCH_PERSIST_VAR_CONVERT_TYPE_FAIL).param(ARG_VAR_NAME, varName));
    }

    public Integer getPersistInt(IBatchTaskContext context, String varName) {
        return ConvertHelper.toInt(getPersistVar(context, varName),
                err -> new NopException(ERR_BATCH_PERSIST_VAR_CONVERT_TYPE_FAIL).param(ARG_VAR_NAME, varName));
    }

    public Boolean getPersistBoolean(IBatchTaskContext context, String varName) {
        return ConvertHelper.toBoolean(getPersistVar(context, varName),
                err -> new NopException(ERR_BATCH_PERSIST_VAR_CONVERT_TYPE_FAIL).param(ARG_VAR_NAME, varName));
    }
}
