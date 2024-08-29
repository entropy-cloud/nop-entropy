package io.nop.demo.biz;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.beans.ApiResponse;
import io.nop.api.core.util.FutureHelper;
import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.graphql.core.IGraphQLExecutionContext;
import io.nop.graphql.core.engine.IGraphQLEngine;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

@NopTestConfig(localDb = true, initDatabaseSchema = true)
public class TestDemoBizModel extends JunitBaseTestCase {

    @Inject
    IGraphQLEngine graphQLEngine;

    @SuppressWarnings("unchecked")
    @Test
    public void testTaskFlow() {
        ApiRequest<Map<String, Object>> request = new ApiRequest<>();
        Map<String, Object> map = new HashMap<>();
        map.put("a", 1);
        map.put("b", 2);
        request.setData(map);
        IGraphQLExecutionContext context = graphQLEngine.newRpcContext(null,
                "Demo__callTask", request);

        ApiResponse<Map<String, Object>> response = (ApiResponse<Map<String, Object>>)
                FutureHelper.syncGet(graphQLEngine.executeRpcAsync(context));

        Map<String, Object> ret = response.get();
        assertEquals(3, ret.get("sum"));
    }
}
