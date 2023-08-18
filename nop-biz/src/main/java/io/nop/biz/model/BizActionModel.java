/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.biz.model;

import io.nop.api.core.util.INeedInit;
import io.nop.biz.BizConstants;
import io.nop.biz.api.IBizActionModel;
import io.nop.biz.model._gen._BizActionModel;
import io.nop.core.context.action.IServiceAction;
import io.nop.core.type.IGenericType;
import io.nop.graphql.core.ast.GraphQLOperationType;

public class BizActionModel extends _BizActionModel implements IBizActionModel, INeedInit {
    private IServiceAction executable;

    public BizActionModel() {

    }

    @Override
    public void init() {
        for (BizActionArgModel argModel : getArgs()) {
            argModel.init();
        }

        if (getReturn() != null) {
            getReturn().init();
        }
    }

    public GraphQLOperationType getOperationType() {
        if (BizConstants.BIZ_ACTION_TYPE_MUTATION.equals(getType()))
            return GraphQLOperationType.mutation;
        if (BizConstants.BIZ_ACTION_TYPE_QUERY.equals(getType()))
            return GraphQLOperationType.query;
        return GraphQLOperationType.action;
    }

    @Override
    public IGenericType getReturnType() {
        BizReturnModel returnModel = getReturn();
        return returnModel == null ? null : returnModel.getSchema().getType();
    }

    @Override
    public boolean isReturnMandatory() {
        BizReturnModel returnModel = getReturn();
        return returnModel != null && returnModel.isMandatory();
    }

    @Override
    public IServiceAction getExecutable() {
        return executable;
    }

    public void setExecutable(IServiceAction executable) {
        this.executable = executable;
    }
}