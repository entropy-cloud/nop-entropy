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

public class DoubleFieldMarshaller implements IFieldMarshaller {
    public static DoubleFieldMarshaller INSTANCE = new DoubleFieldMarshaller();

    @Override
    public String getGrpcTypeName(){
        return "double";
    }

    @Override
    public boolean isObject() {
        return false;
    }

    @Override
    public Object readField(CodedInputStream in) throws IOException {
        return in.readDouble();
    }

    @Override
    public void writeField(CodedOutputStream out, int propId, Object value) throws IOException {
        out.writeDouble(propId, (Double) value);
    }

    @Override
    public void writeFieldNoTag(CodedOutputStream out, Object value) throws IOException {
        out.writeDoubleNoTag((Double) value);
    }

    @Override
    public int computeSize(int propId, Object value) {
        return CodedOutputStream.computeDoubleSize(propId, (Double) value);
    }

    @Override
    public int computeSizeNoTag(Object value) {
        return CodedOutputStream.computeDoubleSizeNoTag((Double) value);
    }

    @Override
    public BinaryScalarType getBinaryScalarType() {
        return BinaryScalarType.DOUBLE;
    }
}
