package io.nop.auth.service;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.beans.ApiResponse;
import io.nop.api.core.beans.FieldSelectionBean;
import io.nop.api.core.beans.graphql.GraphQLRequestBean;
import io.nop.api.core.beans.graphql.GraphQLResponseBean;
import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.graphql.core.IGraphQLExecutionContext;
import io.nop.graphql.core.engine.IGraphQLEngine;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@NopTestConfig(localDb = true, initDatabaseSchema = true)
public class TestGraphQLFragments extends JunitBaseTestCase {

    @Inject
    IGraphQLEngine graphQLEngine;

    @Test
    public void testRestFragments() {
        ApiRequest<Map<String, Object>> request = new ApiRequest<>();
        request.setSelection(FieldSelectionBean.fromProp("...F_defaults", "relatedRoleList"));
        IGraphQLExecutionContext context = graphQLEngine.newRpcContext(null, "NopAuthUser__findList",
                request);
        ApiResponse<?> response = graphQLEngine.executeRpc(context);
        assertTrue(response.isOk());
    }

    @Test
    public void testGraphQLFragments() {
        GraphQLRequestBean request = new GraphQLRequestBean();
        request.setQuery("query { NopAuthUser__findList{ ...F_defaults,relatedRoleList{...F_defaults}} }");
        IGraphQLExecutionContext context = graphQLEngine.newGraphQLContext(request);
        GraphQLResponseBean response = graphQLEngine.executeGraphQL(context);
        assertFalse(response.hasError());
    }
}
