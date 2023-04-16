/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.auth.api.utils;

import io.nop.api.core.auth.IDataAuthChecker;
import io.nop.api.core.auth.ISecurityContext;
import io.nop.api.core.beans.TreeBean;
import io.nop.api.core.beans.query.QueryBean;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.IWithIdentifier;

import java.util.Collection;

import static io.nop.auth.api.AuthApiErrors.ARG_ACTION_NAME;
import static io.nop.auth.api.AuthApiErrors.ARG_BIZ_OBJ_NAME;
import static io.nop.auth.api.AuthApiErrors.ARG_ID;
import static io.nop.auth.api.AuthApiErrors.ARG_USER_NAME;
import static io.nop.auth.api.AuthApiErrors.ERR_AUTH_NO_DATA_AUTH;

@SuppressWarnings("PMD.TooManyStaticImports")
public class AuthHelper {
    public static QueryBean appendFilter(IDataAuthChecker checker, QueryBean query,
                                         String bizObjName, String action, ISecurityContext context) {
        if (checker == null || context.getUserContext() == null)
            return query;

        TreeBean filter = checker.getFilter(bizObjName, action, context);
        if (filter == null)
            return query;

        if (query == null)
            query = new QueryBean();

        query.addFilter(filter);
        return query;
    }

    public static void checkDataAuth(IDataAuthChecker checker,
                                     String bizObjName,
                                     String action,
                                     Object entity, ISecurityContext context) {
        if (checker == null || entity == null || context.getUserContext() == null)
            return;

        if (!checker.isPermitted(bizObjName, action, entity, context))
            throw new NopException(ERR_AUTH_NO_DATA_AUTH)
                    .param(ARG_BIZ_OBJ_NAME, bizObjName)
                    .param(ARG_ACTION_NAME, action)
                    .param(ARG_ID, getId(entity))
                    .param(ARG_USER_NAME, context.getUserContext().getUserName());
    }

    public static void checkDataAuthForList(IDataAuthChecker checker,
                                            String bizObjName,
                                            String action,
                                            Collection<?> entities, ISecurityContext context) {
        if (checker == null || context.getUserContext() == null)
            return;

        for (Object entity : entities) {
            checkDataAuth(checker, bizObjName, action, entity, context);
        }
    }

    static Object getId(Object entity) {
        if (entity instanceof IWithIdentifier)
            return ((IWithIdentifier) entity).get_id();
        return null;
    }
}
