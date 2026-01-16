/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.sys.service;

import io.nop.api.core.annotations.autotest.EnableSnapshot;
import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.annotations.core.OptionalBoolean;
import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.util.FutureHelper;
import io.nop.autotest.junit.JunitAutoTestCase;
import io.nop.graphql.core.IGraphQLExecutionContext;
import io.nop.graphql.core.ast.GraphQLOperationType;
import io.nop.graphql.core.engine.IGraphQLEngine;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;


@NopTestConfig(localDb = true, initDatabaseSchema = OptionalBoolean.TRUE)
public class TestDynamicExtFields extends JunitAutoTestCase {
    @Inject
    IGraphQLEngine graphQLEngine;

    @Test
    public void testSaveExtFields() {
        ApiRequest<?> request = input("request.json5", ApiRequest.class);
        IGraphQLExecutionContext context = graphQLEngine.newRpcContext(GraphQLOperationType.mutation,
                "NopSysCodeRule__save", request);
        Object result = FutureHelper.syncGet(graphQLEngine.executeRpcAsync(context));
        output("response.json5", result);
    }
}
