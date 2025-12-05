/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.graphql.grpc.proto;

import com.google.protobuf.CodedInputStream;
import com.google.protobuf.CodedOutputStream;
import io.nop.commons.type.BinaryScalarType;

import java.io.IOException;

public interface IFieldMarshaller {
    boolean isObject();

    Object readField(CodedInputStream in) throws IOException;

    void writeField(CodedOutputStream out, int propId, Object value) throws IOException;

    void writeFieldNoTag(CodedOutputStream out, Object value) throws IOException;

    int computeSize(int propId, Object value);

    int computeSizeNoTag(Object value);

    String getGrpcTypeName();

    BinaryScalarType getBinaryScalarType();
}