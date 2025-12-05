/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.graphql.grpc.proto.marshaller;

import com.google.protobuf.CodedInputStream;
import com.google.protobuf.CodedOutputStream;
import com.google.protobuf.Empty;
import io.grpc.MethodDescriptor;
import io.nop.commons.type.BinaryScalarType;
import io.nop.graphql.grpc.proto.IFieldMarshaller;
import io.nop.rpc.model.RpcModelConstants;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

public class EmptyMarshaller implements MethodDescriptor.Marshaller<Object>, IFieldMarshaller {
    public static final EmptyMarshaller INSTANCE = new EmptyMarshaller();

    @Override
    public InputStream stream(Object value) {
        return new ByteArrayInputStream(new byte[0]);
    }

    @Override
    public Object parse(InputStream stream) {
        return null;
    }

    @Override
    public boolean isObject() {
        return false;
    }

    @Override
    public Object readField(CodedInputStream in) throws IOException {
        return null;
    }

    @Override
    public void writeField(CodedOutputStream out, int propId, Object value) throws IOException {
        Empty.getDefaultInstance().writeTo(out);
    }

    @Override
    public void writeFieldNoTag(CodedOutputStream out, Object value) throws IOException {
        Empty.getDefaultInstance().writeTo(out);
    }

    @Override
    public int computeSize(int propId, Object value) {
        return 0;
    }

    @Override
    public int computeSizeNoTag(Object value) {
        return 0;
    }

    @Override
    public String getGrpcTypeName() {
        return RpcModelConstants.PROTO_TYPE_EMPTY;
    }

    @Override
    public BinaryScalarType getBinaryScalarType() {
        return BinaryScalarType.VOID;
    }
}
