/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.graphql.grpc.proto;

public class ServiceMethodSchema {
    private GenericObjSchema requestSchema;
    private GenericObjSchema responseSchema;

    public GenericObjSchema getRequestSchema() {
        return requestSchema;
    }

    public void setRequestSchema(GenericObjSchema requestSchema) {
        this.requestSchema = requestSchema;
    }

    public GenericObjSchema getResponseSchema() {
        return responseSchema;
    }

    public void setResponseSchema(GenericObjSchema responseSchema) {
        this.responseSchema = responseSchema;
    }
}
