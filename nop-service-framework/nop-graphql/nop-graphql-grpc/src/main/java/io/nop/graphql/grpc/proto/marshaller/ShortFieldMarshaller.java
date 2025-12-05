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

public class ShortFieldMarshaller implements IFieldMarshaller {
    public static ShortFieldMarshaller INSTANCE = new ShortFieldMarshaller();

    @Override
    public String getGrpcTypeName() {
        return "int32";
    }

    @Override
    public boolean isObject() {
        return false;
    }

    @Override
    public Object readField(CodedInputStream in) throws IOException {
        return in.readInt32();
    }

    @Override
    public void writeField(CodedOutputStream out, int propId, Object value) throws IOException {
        out.writeInt32(propId, ((Short) value).intValue());
    }

    @Override
    public void writeFieldNoTag(CodedOutputStream out, Object value) throws IOException {
        out.writeInt32NoTag(((Short) value).intValue());
    }

    @Override
    public int computeSize(int propId, Object value) {
        return CodedOutputStream.computeInt32Size(propId, ((Short) value).intValue());
    }

    @Override
    public int computeSizeNoTag(Object value) {
        return CodedOutputStream.computeInt32SizeNoTag(((Short) value).intValue());
    }

    @Override
    public BinaryScalarType getBinaryScalarType() {
        return BinaryScalarType.INT32;
    }
}
