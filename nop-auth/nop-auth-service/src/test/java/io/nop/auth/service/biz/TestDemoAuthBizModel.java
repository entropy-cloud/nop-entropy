package io.nop.auth.service.biz;

import io.nop.api.core.beans.ApiRequest;
import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.graphql.core.IGraphQLExecutionContext;
import io.nop.graphql.core.engine.IGraphQLEngine;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestDemoAuthBizModel extends JunitBaseTestCase {

    @Inject
    DemoAuthBizModel bizModel;

    @Inject
    IGraphQLEngine graphQLEngine;

    @Test
    public void testInjectValue() {
        assertEquals("my-test.data", bizModel.testField);
        assertEquals("my-test.value", bizModel.getTestValue());
    }

    @Test
    public void testMyPageBean() {
        IGraphQLExecutionContext ctx = graphQLEngine.newRpcContext(null, "DemoAuth__findPage", new ApiRequest<>());
        Map<String, Object> ret = (Map<String, Object>) graphQLEngine.executeRpc(ctx).get();
        assertEquals(20, ret.get("all"));
    }
}
