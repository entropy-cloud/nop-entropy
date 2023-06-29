package io.nop.auth.service;

import io.nop.api.core.annotations.autotest.NopTestConfig;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 设置localDb=true,initDatabaseSchema=true表示本单元测试使用独立的内存数据库，并且自动初始化数据库中的表定义
 */
@NopTestConfig(localDb = true, initDatabaseSchema = true, enableActionAuth = "true")
public class TestCheckFieldAuth extends JunitBaseTestCase {
    @Inject
    IGraphQLEngine graphQLEngine;

    /**
     * 检查没有修改权限的用户无法提交新建或者修改
     */
    @Test
    public void testCheckReadAuth() {
        UserContextImpl userContext = new UserContextImpl();
        userContext.setUserId("test");
        IUserContext.set(userContext);

        ApiRequest<?> request = new ApiRequest<>();
        request.setSelection(FieldSelectionBean.fromProp("id", "extConfig"));

        try {
            IGraphQLExecutionContext context = graphQLEngine.newRpcContext(GraphQLOperationType.query,
                    "NopAuthSite__findList", request);
            FutureHelper.syncGet(graphQLEngine.executeRpcAsync(context)).get();
            assertTrue(false);
        } catch (NopException e) {
            e.printStackTrace();
            assertEquals(AuthApiErrors.ERR_AUTH_NO_PERMISSION.getErrorCode(), e.getErrorCode());
        }

        userContext.setRoles(CollectionHelper.buildImmutableSet("manager"));

        try {
            IGraphQLExecutionContext context = graphQLEngine.newRpcContext(GraphQLOperationType.query,
                    "NopAuthSite__findList", request);
//            FutureHelper.syncGet(graphQLEngine.executeRpcAsync(context)).get();
        } finally {
            IUserContext.set(null);
        }
    }
}
