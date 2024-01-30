package io.nop.rpc.grpc.proto.marshaller;

import com.google.protobuf.CodedInputStream;
import com.google.protobuf.CodedOutputStream;
import io.nop.commons.type.BinaryScalarType;
import io.nop.rpc.grpc.proto.IFieldMarshaller;

import java.io.IOException;

public class EnumFieldMarshaller implements IFieldMarshaller {
    public static EnumFieldMarshaller INSTANCE = new EnumFieldMarshaller();

    @Override
    public String getGrpcTypeName() {
        return "enum";
    }

    @Override
    public boolean isObject() {
        return false;
    }

    @Override
    public Object readField(CodedInputStream in) throws IOException {
        return in.readEnum();
    }

    @Override
    public void writeField(CodedOutputStream out, int propId, Object value) throws IOException {
        out.writeEnum(propId, (Integer) value);
    }

    @Override
    public void writeFieldNoTag(CodedOutputStream out, Object value) throws IOException {
        out.writeEnumNoTag((Integer) value);
    }

    @Override
    public int computeSize(int propId, Object value) {
        return CodedOutputStream.computeEnumSize(propId, (Integer) value);
    }

    @Override
    public int computeSizeNoTag(Object value) {
        return CodedOutputStream.computeEnumSizeNoTag((Integer) value);
    }

    @Override
    public BinaryScalarType getBinaryScalarType() {
        return BinaryScalarType.INT32;
    }
}