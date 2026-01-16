/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.auth.service;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.annotations.core.OptionalBoolean;
import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.beans.ApiResponse;
import io.nop.autotest.junit.JunitAutoTestCase;
import io.nop.graphql.core.IGraphQLExecutionContext;
import io.nop.graphql.core.engine.IGraphQLEngine;
import org.junit.jupiter.api.Test;

import jakarta.inject.Inject;

@NopTestConfig(localDb = true, initDatabaseSchema = OptionalBoolean.TRUE)
public class TestBizAction extends JunitAutoTestCase {
    @Inject
    IGraphQLEngine graphQLEngine;

    @Test
    public void testMyFindPage() {
        // ApiRequest<LoginRequest> request = request("request.json5", LoginRequest.class);
        GraphQLRequestBean request = input("request.yaml", GraphQLRequestBean.class);

        IGraphQLExecutionContext context = graphQLEngine.newGraphQLContext(request);
        GraphQLResponseBean result = graphQLEngine.executeGraphQL(context);

        output("response.json5", result);
    }

    @Test
    public void testBizAuth() {
        ApiRequest<Map<String, Object>> request = request("request.json5", Map.class);

        IGraphQLExecutionContext context = graphQLEngine.newRpcContext(null, "NopAuthRole__addRoleUsers",
                request);
        ApiResponse<?> result = graphQLEngine.executeRpc(context);

        output("response.json5", result);
    }
}
