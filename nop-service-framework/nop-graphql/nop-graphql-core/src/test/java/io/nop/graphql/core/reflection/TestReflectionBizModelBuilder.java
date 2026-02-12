/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
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
            return ApiResponse.success("sss");
        }
    }

    @Test
    public void testResponse() {
        try {
            ReflectionBizModelBuilder.INSTANCE.build(new MyServiceBizModel(), new TypeRegistry(),new GraphQLBizModels());
            fail();
        } catch (NopException e) {
            System.out.println(e);
            assertEquals(GraphQLErrors.ERR_GRAPHQL_ACTION_RETURN_TYPE_MUST_NOT_BE_API_RESPONSE.getErrorCode(), e.getErrorCode());
        }
    }
}
