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

public class BooleanFieldMarshaller implements IFieldMarshaller {
    public static BooleanFieldMarshaller INSTANCE = new BooleanFieldMarshaller();

    @Override
    public String getGrpcTypeName(){
        return "boolean";
    }

    @Override
    public boolean isObject() {
        return false;
    }

    @Override
    public Object readField(CodedInputStream in) throws IOException {
        return in.readBool();
    }

    @Override
    public void writeField(CodedOutputStream out, int propId, Object value) throws IOException {
        out.writeBool(propId, (Boolean) value);
    }

    @Override
    public void writeFieldNoTag(CodedOutputStream out, Object value) throws IOException {
        out.writeBoolNoTag((Boolean) value);
    }

    @Override
    public int computeSize(int propId, Object value) {
        return CodedOutputStream.computeBoolSize(propId, (Boolean) value);
    }

    @Override
    public int computeSizeNoTag(Object value) {
        return CodedOutputStream.computeBoolSizeNoTag((Boolean) value);
    }

    @Override
    public BinaryScalarType getBinaryScalarType() {
        return BinaryScalarType.BOOL;
    }
}
