package io.nop.auth.service;

import io.nop.api.core.auth.IUserContext;
import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.beans.FieldSelectionBean;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.FutureHelper;
import io.nop.auth.api.AuthApiErrors;
import io.nop.auth.core.login.UserContextImpl;
import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.commons.util.CollectionHelper;
import io.nop.graphql.core.IGraphQLExecutionContext;
import io.nop.graphql.core.ast.GraphQLOperationType;
import io.nop.graphql.core.engine.IGraphQLEngine;
import org.junit.jupiter.api.Test;

import javax.inject.Inject;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestCheckFieldAuth extends JunitBaseTestCase {
    @Inject
    IGraphQLEngine graphQLEngine;

    /**
     * 检查没有修改权限的用户无法提交新建或者修改
     */
    @Test
    public void testCheckReadAuth() {
        IUserContext.set(null);

        ApiRequest<?> request = new ApiRequest<>();
        request.setSelection(FieldSelectionBean.fromProp("id", "extConfig"));

        try {
            IGraphQLExecutionContext context = graphQLEngine.newRpcContext(GraphQLOperationType.query,
                    "NopAuthSite__findPage", request);
            FutureHelper.syncGet(graphQLEngine.executeRpcAsync(context));
        } catch (NopException e) {
            assertEquals(AuthApiErrors.ERR_AUTH_NO_ROLE.getErrorCode(), e.getErrorCode());
        }

        UserContextImpl userContext = new UserContextImpl();
        userContext.setUserId("test");
        userContext.setRoles(CollectionHelper.buildImmutableSet("manager"));
        IUserContext.set(userContext);

        try {
            IGraphQLExecutionContext context = graphQLEngine.newRpcContext(GraphQLOperationType.query,
                    "NopAuthSite__findPage", request);
            FutureHelper.syncGet(graphQLEngine.executeRpcAsync(context));
        } finally {
            IUserContext.set(null);
        }
    }
}
