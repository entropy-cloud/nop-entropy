package io.nop.auth.service;

import io.nop.api.core.annotations.autotest.EnableSnapshot;
import io.nop.api.core.beans.graphql.GraphQLRequestBean;
import io.nop.api.core.beans.graphql.GraphQLResponseBean;
import io.nop.autotest.junit.JunitAutoTestCase;
import io.nop.graphql.core.IGraphQLExecutionContext;
import io.nop.graphql.core.engine.IGraphQLEngine;
import org.junit.jupiter.api.Test;

import javax.inject.Inject;

/**
 * 测试在xbiz模型文件中定义的action
 */
public class TestBizAction extends JunitAutoTestCase {
    @Inject
    IGraphQLEngine graphQLEngine;

    @EnableSnapshot
    @Test
    public void testMyFindPage() {
        // ApiRequest<LoginRequest> request = request("request.json5", LoginRequest.class);
        GraphQLRequestBean request = input("request.yaml", GraphQLRequestBean.class);

        IGraphQLExecutionContext context = graphQLEngine.newGraphQLContext(request);
        GraphQLResponseBean result = graphQLEngine.executeGraphQL(context);

        output("response.json5", result);
    }
}
