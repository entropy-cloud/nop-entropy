/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.rpc.grpc.proto.marshaller;

import com.google.protobuf.CodedInputStream;
import io.grpc.MethodDescriptor;
import io.nop.api.core.exceptions.NopException;
import io.nop.rpc.grpc.proto.GenericObjSchema;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

public class GenericMessageMarshaller implements MethodDescriptor.Marshaller<Object> {
    private final GenericObjSchema schema;

    public GenericMessageMarshaller(GenericObjSchema schema) {
        this.schema = schema;
    }

    @Override
    public InputStream stream(Object value) {
        return new ByteArrayInputStream(schema.toByteArray(value));
    }

    @Override
    public Object parse(InputStream stream) {
        try {
            return schema.parseObject(CodedInputStream.newInstance(stream));
        } catch (Exception e) {
            throw NopException.adapt(e);
        }
    }
}
