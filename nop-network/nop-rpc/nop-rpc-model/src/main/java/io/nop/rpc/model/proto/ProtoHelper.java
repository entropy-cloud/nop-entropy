/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.rpc.model.proto;

import io.nop.api.core.exceptions.NopException;
import io.nop.commons.type.BinaryScalarType;
import io.nop.commons.type.StdDataType;
import io.nop.core.type.IGenericType;
import io.nop.rpc.model.RpcModelConstants;

import static io.nop.rpc.model.RpcModelErrors.ARG_DATA_TYPE;
import static io.nop.rpc.model.RpcModelErrors.ERR_PROTO_NOT_SUPPORT_DATA_TYPE;

public class ProtoHelper {
    public static String getProtoTypeName(IGenericType type) {
        if (type == null || type.isVoidType())
            return RpcModelConstants.PROTO_TYPE_EMPTY;

        if (type.isAnyType())
            return RpcModelConstants.PROTO_TYPE_ANY;

        StdDataType dataType = type.getStdDataType();
        BinaryScalarType scalarType = dataType.toBinaryScalarType();
        if (scalarType.isProtoBufType()) {
            if (scalarType != BinaryScalarType.ANY)
                return scalarType.getText();
        }
        return type.getTypeName();
    }

    public static String getRequestProtoTypeName(String typeName) {
        StdDataType dataType = StdDataType.fromJavaClassName(typeName);
        if (dataType == null)
            return typeName;
        BinaryScalarType scalarType = dataType.toBinaryScalarType();
        if (scalarType == null)
            return typeName;
        return scalarType.toProtoBufTypeName();
    }

    public static String getResponseProtoTypeName(String fullMethodName, IGenericType type) {
        if (type == null || type.isVoidType())
            return RpcModelConstants.PROTO_TYPE_EMPTY;

        if (type.isAnyType())
            return RpcModelConstants.PROTO_TYPE_ANY;

        if (type.isCollectionLike()) {
            return fullMethodName + "_response";
        }

        StdDataType dataType = type.getStdDataType();
        if (dataType == StdDataType.ANY)
            return type.getTypeName();
        return fullMethodName + "_response";
    }

    public static BinaryScalarType toBinaryScalarType(StdDataType dataType) {
        switch (dataType) {
            case INT:
                return BinaryScalarType.INT32;
            case SHORT:
                return BinaryScalarType.INT16;
            case LONG:
                return BinaryScalarType.INT64;
            case FLOAT:
                return BinaryScalarType.FLOAT;
            case DOUBLE:
                return BinaryScalarType.DOUBLE;
            case BOOLEAN:
                return BinaryScalarType.BOOL;
            case BYTES:
                return BinaryScalarType.BYTES;
            case ANY:
                return BinaryScalarType.ANY;
            case VOID:
                return BinaryScalarType.VOID;
            case STRING:
            case DATE:
            case DATETIME:
            case TIMESTAMP:
            case TIME:
            case GEOMETRY:
            case DURATION:
            //case FILE:
            //case FILES:
            case MAP:
                return BinaryScalarType.STRING;
            default:
                throw new NopException(ERR_PROTO_NOT_SUPPORT_DATA_TYPE)
                        .param(ARG_DATA_TYPE, dataType);
        }
    }
}
