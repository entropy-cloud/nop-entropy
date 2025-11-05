/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.biz.model;

import io.nop.biz.model._gen._BizLoaderModel;
import io.nop.core.context.IEvalContext;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.type.IGenericType;

public class BizLoaderModel extends _BizLoaderModel {
    public BizLoaderModel() {

    }

    public IEvalScope newEvalScope(Object[] args, IEvalContext ctx) {
        IEvalScope scope = ctx.getEvalScope().newChildScope();
        for (int i = 0, n = args.length; i < n; i++) {
            String name = getArgs().get(i).getName();
            scope.setLocalValue(null, name, args[i]);
        }
        return scope;
    }

    public IGenericType getReturnType() {
        BizReturnModel returnModel = getReturn();
        if (returnModel == null)
            return null;
        return returnModel.getType();
    }

    public boolean isReturnList() {
        IGenericType returnType = getReturnType();
        if (returnType == null)
            return false;
        return returnType.isListLike();
    }
}
