package io.nop.auth.service;

import io.nop.api.core.beans.graphql.GraphQLRequestBean;
import io.nop.api.core.beans.graphql.GraphQLResponseBean;
import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.graphql.core.IGraphQLExecutionContext;
import io.nop.graphql.core.engine.IGraphQLEngine;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestBaseCrud extends JunitBaseTestCase {
    @Inject
    IGraphQLEngine graphQLEngine;

    /**
     * 只定义biz和meta文件，在xbiz根节点上标注graphql:base="crud"
     */
    @Test
    public void testFindPage() {
        GraphQLRequestBean request = new GraphQLRequestBean();
        request.setQuery("query { NopAuthUserEx__findPage{ items{ id, userName } } }");
        IGraphQLExecutionContext context = graphQLEngine.newGraphQLContext(request);
        GraphQLResponseBean response = graphQLEngine.executeGraphQL(context);
        assertTrue(!response.hasError());
    }

    @Test
    public void testActiveFindPage() {
        GraphQLRequestBean request = new GraphQLRequestBean();
        request.setQuery("query { NopAuthUserEx__active_findPage2{ items{ id, userName } } }");
        IGraphQLExecutionContext context = graphQLEngine.newGraphQLContext(request);
        GraphQLResponseBean response = graphQLEngine.executeGraphQL(context);
        assertTrue(!response.hasError());
    }
}
