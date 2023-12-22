package io.nop.graphql.core.reflection;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.api.core.annotations.biz.BizMutation;
import io.nop.api.core.beans.ApiResponse;
import io.nop.api.core.exceptions.NopException;
import io.nop.graphql.core.GraphQLErrors;
import io.nop.graphql.core.schema.TypeRegistry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

public class TestReflectionBizModelBuilder {
    @BizModel("MyService")
    public static class MyServiceBizModel {
        @BizMutation
        public ApiResponse<String> myMethod() {
            return ApiResponse.buildSuccess("sss");
        }
    }

    @Test
    public void testResponse() {
        try {
            ReflectionBizModelBuilder.INSTANCE.build(new MyServiceBizModel(), new TypeRegistry());
            fail();
        } catch (NopException e) {
            System.out.println(e);
            assertEquals(GraphQLErrors.ERR_GRAPHQL_ACTION_RETURN_TYPE_MUST_NOT_BE_API_RESPONSE.getErrorCode(), e.getErrorCode());
        }
    }
}
