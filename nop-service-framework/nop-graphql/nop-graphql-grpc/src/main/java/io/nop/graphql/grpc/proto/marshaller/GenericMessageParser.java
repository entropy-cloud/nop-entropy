/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.graphql.grpc.proto.marshaller;

import com.google.protobuf.CodedInputStream;
import com.google.protobuf.ExtensionRegistryLite;
import com.google.protobuf.InvalidProtocolBufferException;
import io.nop.api.core.exceptions.NopException;
import io.nop.graphql.grpc.proto.DynamicMessage;
import io.nop.graphql.grpc.proto.GenericObjSchema;
import io.nop.graphql.grpc.proto.ProtobufMarshallerHelper;

import java.io.IOException;

public class GenericMessageParser extends AbstractMessageParser<DynamicMessage> {
    private final GenericObjSchema schema;

    public GenericMessageParser(GenericObjSchema schema) {
        this.schema = schema;
    }

    @Override
    public DynamicMessage parsePartialFrom(CodedInputStream input, ExtensionRegistryLite extensionRegistry) throws InvalidProtocolBufferException {
        try {
            return new DynamicMessage(schema, ProtobufMarshallerHelper.parseObject(input, schema));
        } catch (IOException e) {
            throw NopException.adapt(e);
        }
    }
}
