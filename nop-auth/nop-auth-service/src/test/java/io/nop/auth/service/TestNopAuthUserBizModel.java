/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.auth.service;

import io.nop.api.core.annotations.autotest.EnableSnapshot;
import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.auth.IUserContext;
import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.util.FutureHelper;
import io.nop.auth.core.login.UserContextImpl;
import io.nop.auth.dao.entity.NopAuthUser;
import io.nop.autotest.junit.JunitAutoTestCase;
import io.nop.core.type.IGenericType;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import io.nop.graphql.core.IGraphQLExecutionContext;
import io.nop.graphql.core.ast.GraphQLOperationType;
import io.nop.graphql.core.engine.IGraphQLEngine;
import io.nop.xlang.api.XLang;
import io.nop.xlang.xdef.IStdDomainHandler;
import io.nop.xlang.xdef.domain.StdDomainRegistry;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

@NopTestConfig(enableActionAuth = "false", initDatabaseSchema = true)
public class TestNopAuthUserBizModel extends JunitAutoTestCase {

    @Inject
    IGraphQLEngine graphQLEngine;

    @Inject
    IDaoProvider daoProvider;

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
}
