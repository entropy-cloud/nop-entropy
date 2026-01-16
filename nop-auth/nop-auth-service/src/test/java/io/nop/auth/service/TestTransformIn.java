package io.nop.auth.service;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.annotations.core.OptionalBoolean;
import io.nop.api.core.beans.ApiRequest;
import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.graphql.core.IGraphQLExecutionContext;
import io.nop.graphql.core.engine.IGraphQLEngine;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

@NopTestConfig(localDb = true, initDatabaseSchema = OptionalBoolean.TRUE)
public class TestTransformIn extends JunitBaseTestCase {

    @Inject
    IGraphQLEngine graphQLEngine;

    @Test
    public void testTransformIn() {
        ApiRequest<Map<String, Object>> req = new ApiRequest<>();
        Map<String, Object> data = new HashMap<>();
        data.put("name", "a");
        data.put("remark", "B");

        Map<String, Object> m = new HashMap<>();
        m.put("data", data);
        req.setData(m);

        IGraphQLExecutionContext gqlCtx = graphQLEngine.newRpcContext(null,
                "NopAuthPosition__save", req);

        Map<String, Object> map = (Map<String, Object>) graphQLEngine.executeRpc(gqlCtx).get();
        assertEquals("BEXT", map.get("remark"));
    }
}