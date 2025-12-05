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
import io.nop.commons.type.BinaryScalarType;
import io.nop.graphql.grpc.proto.IFieldMarshaller;

import java.io.IOException;

public class BytesFieldMarshaller implements IFieldMarshaller {
    public static BytesFieldMarshaller INSTANCE = new BytesFieldMarshaller();

    @Override
    public String getGrpcTypeName(){
        return "bytes";
    }
    @Override
    public boolean isObject() {
        return false;
    }

    @Override
    public Object readField(CodedInputStream in) throws IOException {
        return in.readByteArray();
    }

    @Override
    public void writeField(CodedOutputStream out, int propId, Object value) throws IOException {
        out.writeByteArray(propId, (byte[]) value);
    }

    @Override
    public void writeFieldNoTag(CodedOutputStream out, Object value) throws IOException {
        out.writeByteArrayNoTag((byte[]) value);
    }

    @Override
    public int computeSize(int propId, Object value) {
        return CodedOutputStream.computeByteArraySize(propId, (byte[]) value);
    }

    @Override
    public int computeSizeNoTag(Object value) {
        return CodedOutputStream.computeByteArraySizeNoTag((byte[]) value);
    }

    @Override
    public BinaryScalarType getBinaryScalarType() {
        return BinaryScalarType.BYTES;
    }
}
