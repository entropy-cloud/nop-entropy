/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.biz.impl;

import io.nop.api.core.auth.IBizAuthChecker;
import io.nop.api.core.auth.ISecurityContext;
import io.nop.api.core.beans.FieldSelectionBean;
import io.nop.api.core.exceptions.NopException;
import io.nop.auth.api.AuthApiErrors;
import io.nop.biz.BizConstants;
import io.nop.biz.api.IBizObject;
import io.nop.biz.api.IBizObjectManager;
import io.nop.commons.util.StringHelper;
import io.nop.core.context.IServiceContext;
import io.nop.graphql.core.ast.GraphQLFieldDefinition;
import io.nop.graphql.core.ast.GraphQLObjectDefinition;
import io.nop.graphql.core.engine.GraphQLActionAuthChecker;
import io.nop.orm.OrmConstants;
import jakarta.inject.Inject;

import java.util.HashMap;
import java.util.Map;

import static io.nop.auth.api.AuthApiErrors.ARG_OBJ_TYPE_NAME;
import static io.nop.biz.BizErrors.ARG_BIZ_OBJ_NAME;
import static io.nop.biz.BizErrors.ARG_PROP_NAME;
import static io.nop.biz.BizErrors.ERR_BIZ_UNKNOWN_PROP;

public class DefaultBizAuthChecker implements IBizAuthChecker {
    private IBizObjectManager bizObjectManager;

    @Inject
    public void setBizObjectManager(IBizObjectManager bizObjectManager) {
        this.bizObjectManager = bizObjectManager;
    }

    @Override
    public boolean checkAuth(String bizObjName, String objId, String fieldName, ISecurityContext context) {
        IBizObject bizObject = bizObjectManager.getBizObject(bizObjName);
        IServiceContext ctx = (IServiceContext) context;
        Map<String, Object> input = new HashMap<>();
        input.put(OrmConstants.PROP_ID, objId);
        bizObject.invoke(BizConstants.METHOD_GET, input, FieldSelectionBean.fromProp(OrmConstants.PROP_ID), ctx);
        GraphQLObjectDefinition objDef = bizObject.getObjectDefinition();

        if (!StringHelper.isEmpty(fieldName)) {
            GraphQLFieldDefinition field = objDef.getField(fieldName);
            if (field == null)
                throw new NopException(ERR_BIZ_UNKNOWN_PROP)
                        .param(ARG_BIZ_OBJ_NAME, bizObjName)
                        .param(ARG_PROP_NAME, fieldName);

            if (field.getAuth() != null) {
                return GraphQLActionAuthChecker.checkAuth(bizObjName, fieldName, field.getAuth(), ctx.getActionAuthChecker(), ctx, false);
            }
        }

        return true;
    }

    @Override
    public void forceCheckAuth(String bizObjName, String objId, String fieldName, ISecurityContext context) {
        if (!checkAuth(bizObjName, objId, fieldName, context))
            throw new NopException(AuthApiErrors.ERR_AUTH_NO_PERMISSION)
                    .param(AuthApiErrors.ARG_ACTION_NAME, fieldName)
                    .param(ARG_OBJ_TYPE_NAME, bizObjName);
    }
}
