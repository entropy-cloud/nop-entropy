/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.auth.core.model;

import io.nop.api.core.auth.ISecurityContext;
import io.nop.api.core.auth.IUserContext;
import io.nop.api.core.beans.FilterBeanConstants;
import io.nop.api.core.exceptions.NopException;
import io.nop.auth.core.AuthCoreConstants;
import io.nop.auth.core.model._gen._ObjDataAuthModel;
import io.nop.core.CoreConstants;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.lang.xml.XNode;
import io.nop.xlang.api.XLang;

import static io.nop.auth.api.AuthApiErrors.ARG_BIZ_OBJ_NAME;
import static io.nop.auth.api.AuthApiErrors.ARG_USER_NAME;
import static io.nop.auth.api.AuthApiErrors.ERR_AUTH_NO_DATA_AUTH;

public class ObjDataAuthModel extends _ObjDataAuthModel {

    public ObjDataAuthModel() {

    }

    public void sort(){
        this.getRoleAuths().sort(RoleDataAuthModel::compareTo);
    }

    public boolean isPermitted(String action, Object entity, ISecurityContext context) {
        IEvalScope scope = XLang.newEvalScope();
        scope.setLocalValue(AuthCoreConstants.VAR_ACTION, action);
        scope.setLocalValue(AuthCoreConstants.VAR_ENTITY, entity);
        scope.setLocalValue(AuthCoreConstants.VAR_USER_CONTEXT, context.getUserContext());
        scope.setLocalValue(CoreConstants.VAR_SVC_CTX, context);

        IUserContext userContext = context.getUserContext();

        boolean hasRoleAuth = false;
        for (RoleDataAuthModel roleAuth : this.getRoleAuths()) {
            String roleId = roleAuth.getRoleId();
            if (userContext.isUserInRole(roleId)) {
                hasRoleAuth = true;
                if (roleAuth.getCheck() != null && !roleAuth.getCheck().passConditions(scope)) {
                    return false;
                }
                break;
            }
        }

        return hasRoleAuth;
    }

    public XNode getFilter(String action, ISecurityContext context) {
        XNode filter = XNode.make(FilterBeanConstants.FILTER_OP_AND);

        IUserContext userContext = context.getUserContext();

        IEvalScope scope = XLang.newEvalScope();
        scope.setLocalValue(AuthCoreConstants.VAR_ACTION, action);
        scope.setLocalValue(AuthCoreConstants.VAR_FILTER, filter);
        scope.setLocalValue(AuthCoreConstants.VAR_USER_CONTEXT, userContext);
        scope.setLocalValue(CoreConstants.VAR_SVC_CTX, context);

        boolean hasRoleAuth = false;

        for (RoleDataAuthModel roleAuth : this.getRoleAuths()) {

            String roleId = roleAuth.getRoleId();
            if (userContext.isUserInRole(roleId)) {
                hasRoleAuth = true;

                if (roleAuth.getFilter() != null) {
                    XNode node = roleAuth.getFilter().generateNode(scope);
                    if (node != null) {
                        if (node.isDummyNode()) {
                            filter.appendChildren(node.detachChildren());
                        } else {
                            filter.appendChild(node);
                        }
                    }
                }
                break;
            }
        }

        if (!hasRoleAuth)
            throw new NopException(ERR_AUTH_NO_DATA_AUTH)
                    .source(this).param(ARG_BIZ_OBJ_NAME, getName())
                    .param(ARG_USER_NAME, userContext.getUserName());

        if (!filter.hasChild()) {
            return null;
        }

        return filter;
    }
}