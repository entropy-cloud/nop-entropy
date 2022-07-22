package io.nop.graphql.core.engine;

import io.nop.api.core.beans.graphql.GraphQLRequestBean;
import io.nop.api.core.beans.graphql.GraphQLResponseBean;
import io.nop.api.core.util.FutureHelper;
import io.nop.core.lang.json.JsonTool;
import io.nop.core.unittest.BaseTestCase;
import io.nop.graphql.core.IGraphQLExecutionContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletionStage;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestGraphQLEngine extends BaseTestCase {
    GraphQLEngine engine;

    @BeforeEach
    public void setUp() {
        engine = new GraphQLEngine();
        engine.setSchemaLoader(new MockGraphQLSchemaLoader());
    }

    @Test
    public void testLoad() {
        GraphQLRequestBean request = attachmentBean("request.yaml", GraphQLRequestBean.class);
        IGraphQLExecutionContext context = engine.newExecutionContext(request);
        CompletionStage<GraphQLResponseBean> promise = engine.executeAsync(context);
        GraphQLResponseBean response = FutureHelper.syncGet(promise);
        System.out.println(JsonTool.serialize(response, true));
        assertEquals(attachmentJsonText("response.json"), JsonTool.serialize(response, true));
    }
}
