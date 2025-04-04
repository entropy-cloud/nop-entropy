/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.auth.service;

import io.nop.api.core.annotations.autotest.EnableSnapshot;
import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.auth.IUserContext;
import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.beans.ApiResponse;
import io.nop.api.core.beans.graphql.GraphQLRequestBean;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.ioc.BeanContainer;
import io.nop.api.core.util.FutureHelper;
import io.nop.auth.core.login.UserContextImpl;
import io.nop.auth.dao.entity.NopAuthUser;
import io.nop.auth.service.entity.NopAuthUserBizModel;
import io.nop.autotest.junit.JunitAutoTestCase;
import io.nop.core.context.IServiceContext;
import io.nop.core.lang.json.JsonTool;
import io.nop.core.model.selection.FieldSelectionBeanParser;
import io.nop.core.type.IGenericType;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import io.nop.graphql.core.IGraphQLExecutionContext;
import io.nop.graphql.core.ast.GraphQLOperationType;
import io.nop.graphql.core.engine.IGraphQLEngine;
import io.nop.graphql.core.jsonrpc.JsonRpcService;
import io.nop.ioc.IocErrors;
import io.nop.orm.IOrmTemplate;
import io.nop.xlang.api.XLang;
import io.nop.xlang.xdef.IStdDomainHandler;
import io.nop.xlang.xdef.domain.StdDomainRegistry;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.concurrent.CompletionStage;

import static io.nop.auth.service.AuthTestHelper.saveRole;
import static io.nop.auth.service.AuthTestHelper.saveUser;
import static io.nop.auth.service.AuthTestHelper.saveUserRole;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

@NopTestConfig(enableActionAuth = "false", initDatabaseSchema = true)
public class TestNopAuthUserBizModel extends JunitAutoTestCase {

    @Inject
    IGraphQLEngine graphQLEngine;

    @Inject
    IDaoProvider daoProvider;

    @Inject
    IOrmTemplate ormTemplate;

    @EnableSnapshot
    @Test
    public void testFindBeanByType() {
        try {
            BeanContainer.getBeanByType(NopAuthUserBizModel.class);
            fail();
        } catch (NopException e) {
            assertEquals(IocErrors.ERR_IOC_MULTIPLE_BEAN_WITH_TYPE.getErrorCode(), e.getErrorCode());
        }
    }

    @EnableSnapshot
    @Test
    public void testChangeSelfPass() {
        IEntityDao<NopAuthUser> dao = daoProvider.daoFor(NopAuthUser.class);
        NopAuthUser example = new NopAuthUser();
        example.setUserName("nop");
        NopAuthUser user = dao.findFirstByExample(example);

        UserContextImpl userContext = new UserContextImpl();
        userContext.setUserId(user.getUserId());
        IUserContext.set(userContext);

        ApiRequest<?> request = input("request.json5", ApiRequest.class);
        IGraphQLExecutionContext context = graphQLEngine.newRpcContext(GraphQLOperationType.mutation,
                "NopAuthUser__changeSelfPassword", request);
        Object result = FutureHelper.syncGet(graphQLEngine.executeRpcAsync(context));
        output("response.json5", result);
    }

    /**
     * 通过rest方式调用findPage/findFirst等方法的时候，可以传递filter_status=3这种过滤条件，
     * 然后自动转换为标准的QueryBean查询对象
     */
    @EnableSnapshot
    @Test
    public void testQueryBeanNormalizer() {
        ApiRequest<?> request = input("request.json5", ApiRequest.class);
        IGraphQLExecutionContext context = graphQLEngine.newRpcContext(GraphQLOperationType.query,
                "NopAuthUser__findPage", request);
        Object result = FutureHelper.syncGet(graphQLEngine.executeRpcAsync(context));
        output("response.json5", result);
    }

    @EnableSnapshot
    @Test
    public void testParseType() {
        IStdDomainHandler handler = StdDomainRegistry.instance().getStdDomainHandler("generic-type");
        IGenericType type = (IGenericType) handler.parseProp(null, null, "a", NopAuthUser.class.getName(), XLang.newCompileTool());
        type.isAssignableTo(NopAuthUser.class);
    }

    @EnableSnapshot
    @Test
    public void testFindFirst() {
        IGraphQLExecutionContext context = graphQLEngine.newRpcContext(GraphQLOperationType.query,
                "NopAuthUser__findFirst", new ApiRequest<>());
        Object result = FutureHelper.syncGet(graphQLEngine.executeRpcAsync(context));
        output("response.json5", result);
    }

    @EnableSnapshot
    @Test
    public void testNestedFragments() {
        prepareData();
        ApiRequest<Object> request = new ApiRequest<>();
        request.setSelection(new FieldSelectionBeanParser().parseFromText(null, "...F_defaults,role{...F_defaults}"));
        IGraphQLExecutionContext context = graphQLEngine.newRpcContext(GraphQLOperationType.query,
                "NopAuthUserRole__findFirst", request);
        Object result = FutureHelper.syncGet(graphQLEngine.executeRpcAsync(context));
        output("response.json5", result);

        GraphQLRequestBean gqlReq = input("request.json5", GraphQLRequestBean.class);
        context = graphQLEngine.newGraphQLContext(gqlReq);
        result = FutureHelper.syncGet(graphQLEngine.executeGraphQLAsync(context));
        output("response2.json5", result);
    }

    private void prepareData() {
        saveUser("user1");
        saveRole("test");
        saveUserRole("user1", "test");
    }

    @EnableSnapshot
    @Test
    public void testFetchResult() {
        prepareData();
        ormTemplate.runInSession(() -> {
            IEntityDao<NopAuthUser> user = daoProvider.daoFor(NopAuthUser.class);
            List<NopAuthUser> list = user.findAll();
            IServiceContext svcCtx = null; // 在后端模板运行时上下文中一般存在svcCtx
            CompletionStage<Object> future = graphQLEngine.fetchResult(list,
                    "NopAuthUser", "...F_defaults,status_label,relatedRoleList", svcCtx);
            output("result.json5", FutureHelper.syncGet(future));
        });
    }

    @EnableSnapshot
    @Test
    public void testInitData() {
        ApiRequest<Object> request = new ApiRequest<>();
        request.setSelection(new FieldSelectionBeanParser().parseFromText(null, "id,userName,roleMappings{roleId,userId}"));

        IGraphQLExecutionContext context = graphQLEngine.newRpcContext(GraphQLOperationType.query,
                "NopAuthUserEx__initData", request);
        Object result = FutureHelper.syncGet(graphQLEngine.executeRpcAsync(context));
        output("response.json5", result);
    }


    @Inject
    JsonRpcService jsonRpcService;

    @EnableSnapshot
    @Test
    public void testJsonRpc() {
        String body = inputText("input.json");
        ApiResponse<String> response = FutureHelper.syncGet(jsonRpcService.executeAsync(body, new HashMap<>()));
        assertEquals(200, response.getHttpStatus());
        output("response.json5", JsonTool.parse(response.getData()));
    }
}
