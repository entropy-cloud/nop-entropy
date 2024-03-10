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
import io.nop.api.core.beans.graphql.GraphQLRequestBean;
import io.nop.api.core.beans.graphql.GraphQLResponseBean;
import io.nop.autotest.junit.JunitAutoTestCase;
import io.nop.graphql.core.GraphQLConfigs;
import io.nop.graphql.core.IGraphQLExecutionContext;
import io.nop.graphql.core.engine.IGraphQLEngine;
import org.junit.jupiter.api.Test;

import jakarta.inject.Inject;

@NopTestConfig(localDb = true)
public class TestIntrospectionQuery extends JunitAutoTestCase {
    @Inject
    IGraphQLEngine engine;

    public TestIntrospectionQuery() {
        setTestConfig(GraphQLConfigs.CFG_GRAPHQL_SCHEMA_INTROSPECTION_ENABLED, true);
    }

    @Test
    @EnableSnapshot
    public void testQuery() {
        GraphQLRequestBean request = input("request.yaml", GraphQLRequestBean.class);
        IGraphQLExecutionContext context = engine.newGraphQLContext(request);
        GraphQLResponseBean response = engine.executeGraphQL(context);
        output("response.json5", response);
    }
}
