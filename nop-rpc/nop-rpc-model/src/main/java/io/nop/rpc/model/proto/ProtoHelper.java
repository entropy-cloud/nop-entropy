package io.nop.rpc.model.proto;

import io.nop.commons.type.BinaryScalarType;
import io.nop.commons.type.StdDataType;
import io.nop.core.type.IGenericType;
import io.nop.rpc.model.RpcModelConstants;

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
}
