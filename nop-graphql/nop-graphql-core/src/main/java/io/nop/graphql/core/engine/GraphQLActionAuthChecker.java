/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.graphql.core.engine;

import io.nop.api.core.auth.ActionAuthMeta;
import io.nop.api.core.auth.IActionAuthChecker;
import io.nop.api.core.auth.IUserContext;
import io.nop.api.core.beans.FieldSelectionBean;
import io.nop.api.core.exceptions.NopException;
import io.nop.auth.api.AuthApiErrors;
import io.nop.core.context.IServiceContext;
import io.nop.graphql.core.IGraphQLExecutionContext;
import io.nop.graphql.core.ast.GraphQLFieldSelection;
import io.nop.graphql.core.ast.GraphQLFragmentSelection;
import io.nop.graphql.core.ast.GraphQLOperation;
import io.nop.graphql.core.ast.GraphQLSelection;
import io.nop.graphql.core.ast.GraphQLSelectionSet;

import static io.nop.auth.api.AuthApiErrors.ARG_OBJ_TYPE_NAME;
import static io.nop.auth.api.AuthApiErrors.ARG_PERMISSION;
import static io.nop.auth.api.AuthApiErrors.ARG_ROLES;

public class GraphQLActionAuthChecker {
    // static final Logger LOG = LoggerFactory.getLogger(GraphQLActionAuthChecker.class);

    public static GraphQLActionAuthChecker INSTANCE = new GraphQLActionAuthChecker();

    public void check(IGraphQLExecutionContext context) {
        IUserContext userContext = context.getUserContext();
        if (userContext == null)
            return;

        IActionAuthChecker checker = context.getActionAuthChecker();
        if (checker == null)
            return;

        GraphQLOperation operation = context.getOperation();
        FieldSelectionBean selectionBean = context.getFieldSelection();

        String objTypeName = operation.getSelectionSet().getObjTypeName();

        for (GraphQLSelection field : operation.getSelectionSet().getSelections()) {
            GraphQLFieldSelection selection = (GraphQLFieldSelection) field;
            FieldSelectionBean subSelection = selectionBean.getField(selection.getAliasOrName());
            if (subSelection == null)
                continue;

            checkAuth(objTypeName, selection, checker, context.getServiceContext(), true);

            checkSelectionSet(selection.getSelectionSet(), subSelection, checker, userContext, context);
        }
    }

    void checkSelectionSet(GraphQLSelectionSet selectionSet, FieldSelectionBean selectionBean,
                           IActionAuthChecker checker, IUserContext userContext, IGraphQLExecutionContext context) {
        if (selectionSet == null)
            return;

        String objTypeName = selectionSet.getObjTypeName();

        for (GraphQLSelection selection : selectionSet.getSelections()) {
            if (selection instanceof GraphQLFragmentSelection) {
                GraphQLFragmentSelection fragmentSelection = (GraphQLFragmentSelection) selection;
                checkSelectionSet(fragmentSelection.getResolvedFragment().getSelectionSet(), selectionBean, checker,
                        userContext, context);
            } else {
                GraphQLFieldSelection fieldSelection = (GraphQLFieldSelection) selection;
                FieldSelectionBean subSelection = selectionBean.getField(fieldSelection.getAliasOrName());
                if (subSelection == null)
                    continue;

                if (checkAuth(objTypeName, fieldSelection, checker, context.getServiceContext(), false)) {
                    checkSelectionSet(fieldSelection.getSelectionSet(), subSelection, checker, userContext, context);
                } else {
                    selectionBean.removeField(fieldSelection.getAliasOrName());
                }
            }
        }
    }

    public static boolean checkAuth(String objTypeName, GraphQLFieldSelection fieldSelection, IActionAuthChecker checker,
                                    IServiceContext context, boolean action) {
        return checkAuth(objTypeName, fieldSelection.getName(), fieldSelection.getFieldDefinition().getAuth(), checker, context, action);
    }

    public static boolean checkAuth(String objTypeName, String fieldName, ActionAuthMeta auth, IActionAuthChecker checker,
                                    IServiceContext context, boolean action) {
        if (isAllowAccess(auth, context))
            return true;

        IUserContext userContext = context.getUserContext();
        if (userContext == null)
            throw new IllegalStateException("nop.err.auth.no-user-context");

        if (action) {
            throw new NopException(AuthApiErrors.ERR_AUTH_NO_PERMISSION)
                    .param(AuthApiErrors.ARG_ACTION_NAME, fieldName)
                    .param(ARG_PERMISSION, auth.getPermissions())
                    .param(ARG_ROLES, auth.getRoles())
                    .param(ARG_OBJ_TYPE_NAME, objTypeName);
        } else {
            if (auth.isSkipWhenNoAuth())
                return false;
            throw new NopException(AuthApiErrors.ERR_AUTH_NO_PERMISSION_FOR_FIELD)
                    .param(AuthApiErrors.ARG_FIELD_NAME, fieldName)
                    .param(ARG_PERMISSION, auth.getPermissions())
                    .param(ARG_ROLES, auth.getRoles())
                    .param(ARG_OBJ_TYPE_NAME, objTypeName);
        }
    }

    public static boolean isAllowAccess(ActionAuthMeta auth, IServiceContext context) {
        if (auth == null)
            return true;

        IActionAuthChecker checker = context.getActionAuthChecker();
        if (checker == null)
            return true;

        // 如果是公开方法，则不检查用户权限
        if (auth.isPublicAccess())
            return true;

        IUserContext userContext = context.getUserContext();
        if (userContext == null)
            return false;

        if (auth.getRoles() != null && !auth.getRoles().isEmpty()) {
            if (userContext.isUserInAnyRole(auth.getRoles()))
                return true;
        }

        if (auth.getPermissions() != null && !auth.getPermissions().isEmpty()) {
            if (checker.isPermissionSetSatisfied(auth.getPermissions(), context))
                return true;
        }

        return false;
    }
}