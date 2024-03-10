/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.rpc.grpc.proto.marshaller;

import com.google.protobuf.CodedInputStream;
import com.google.protobuf.CodedOutputStream;
import io.nop.commons.type.BinaryScalarType;
import io.nop.rpc.grpc.proto.IFieldMarshaller;

import java.io.IOException;

public class StringFieldMarshaller implements IFieldMarshaller {
    public static StringFieldMarshaller INSTANCE = new StringFieldMarshaller();

    @Override
    public String getGrpcTypeName() {
        return "string";
    }

    @Override
    public boolean isObject() {
        return false;
    }

    @Override
    public Object readField(CodedInputStream in) throws IOException {
        return in.readStringRequireUtf8();
    }

    @Override
    public void writeField(CodedOutputStream out, int propId, Object value) throws IOException {
        out.writeString(propId, (String) value);
    }

    @Override
    public void writeFieldNoTag(CodedOutputStream out, Object value) throws IOException {
        out.writeStringNoTag((String) value);
    }

    @Override
    public int computeSize(int propId, Object value) {
        return CodedOutputStream.computeStringSize(propId, (String) value);
    }

    @Override
    public int computeSizeNoTag(Object value) {
        return CodedOutputStream.computeStringSizeNoTag((String) value);
    }

    @Override
    public BinaryScalarType getBinaryScalarType() {
        return BinaryScalarType.STRING;
    }
}
