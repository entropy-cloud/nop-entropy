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
            case MAP:
                return BinaryScalarType.STRING;
            default:
                throw new NopException(ERR_PROTO_NOT_SUPPORT_DATA_TYPE)
                        .param(ARG_DATA_TYPE, dataType);
        }
    }
}
