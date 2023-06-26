/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.graphql.core.engine;

import io.nop.api.core.auth.ActionAuthMeta;
import io.nop.api.core.auth.IActionAuthChecker;
import io.nop.api.core.auth.IUserContext;
import io.nop.api.core.beans.FieldSelectionBean;
import io.nop.api.core.exceptions.NopException;
import io.nop.auth.api.AuthApiErrors;
import io.nop.commons.util.CollectionHelper;
import io.nop.graphql.core.IGraphQLExecutionContext;
import io.nop.graphql.core.ast.GraphQLFieldDefinition;
import io.nop.graphql.core.ast.GraphQLFieldSelection;
import io.nop.graphql.core.ast.GraphQLFragmentSelection;
import io.nop.graphql.core.ast.GraphQLOperation;
import io.nop.graphql.core.ast.GraphQLSelection;
import io.nop.graphql.core.ast.GraphQLSelectionSet;

import java.util.Set;

import static io.nop.auth.api.AuthApiErrors.ARG_PERMISSION;
import static io.nop.graphql.core.GraphQLErrors.ARG_FIELD_NAME;

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

        for (GraphQLSelection field : operation.getSelectionSet().getSelections()) {
            GraphQLFieldSelection selection = (GraphQLFieldSelection) field;
            FieldSelectionBean subSelection = selectionBean.getField(selection.getAliasOrName());
            if (subSelection == null)
                continue;

            checkAuth(selection, checker, context);

            checkSelectionSet(selection.getSelectionSet(), subSelection, checker, userContext, context);
        }
    }

    void checkSelectionSet(GraphQLSelectionSet selectionSet, FieldSelectionBean selectionBean,
                           IActionAuthChecker checker, IUserContext userContext, IGraphQLExecutionContext context) {
        if (selectionSet == null)
            return;

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

                checkAuth(fieldSelection, checker, context);

                checkSelectionSet(fieldSelection.getSelectionSet(), subSelection, checker, userContext, context);
            }
        }
    }

    void checkAuth(GraphQLFieldSelection selection, IActionAuthChecker checker, IGraphQLExecutionContext context) {
        GraphQLFieldDefinition field = selection.getFieldDefinition();
        ActionAuthMeta auth = field.getAuth();
        if (auth == null)
            return;

        IUserContext userContext = context.getUserContext();
        if (!CollectionHelper.isEmpty(auth.getRoles())) {
            if (userContext.isUserInAnyRole(auth.getRoles()))
                return;
        }

        if (auth.getPermissions() != null && !auth.getPermissions().isEmpty()) {
            for (Set<String> permissions : auth.getPermissions()) {
                if (isPermitted(permissions, checker, context))
                    return;
            }
            throw new NopException(AuthApiErrors.ERR_AUTH_NO_PERMISSION).source(field)
                    .param(ARG_PERMISSION, auth.getPermissions()).param(ARG_FIELD_NAME, field.getName());
        }
    }

    private boolean isPermitted(Set<String> permissions, IActionAuthChecker checker, IGraphQLExecutionContext context) {
        for (String permission : permissions) {
            if (!checker.isPermitted(permission, context))
                return false;
        }
        return true;
    }
}