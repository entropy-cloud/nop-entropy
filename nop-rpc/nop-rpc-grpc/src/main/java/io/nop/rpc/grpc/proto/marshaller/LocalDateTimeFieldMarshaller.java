package io.nop.rpc.grpc.proto.marshaller;

import com.google.protobuf.CodedInputStream;
import com.google.protobuf.CodedOutputStream;
import io.nop.commons.type.BinaryScalarType;
import io.nop.commons.util.DateHelper;
import io.nop.rpc.grpc.proto.IFieldMarshaller;

import java.io.IOException;

public class LocalDateTimeFieldMarshaller implements IFieldMarshaller {
    public static LocalDateTimeFieldMarshaller INSTANCE = new LocalDateTimeFieldMarshaller();

    static final int SIZE_NO_TAG = CodedOutputStream.computeStringSizeNoTag("2002-01-02 11:02:03");

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
        return DateHelper.parseDataTime(in.readStringRequireUtf8());
    }

    @Override
    public void writeField(CodedOutputStream out, int propId, Object value) throws IOException {
        out.writeString(propId, value.toString());
    }

    @Override
    public void writeFieldNoTag(CodedOutputStream out, Object value) throws IOException {
        out.writeStringNoTag(value.toString());
    }

    @Override
    public int computeSize(int propId, Object value) {
        return CodedOutputStream.computeTagSize(propId) + SIZE_NO_TAG;
    }

    @Override
    public int computeSizeNoTag(Object value) {
        return SIZE_NO_TAG;
    }

    @Override
    public BinaryScalarType getBinaryScalarType() {
        return BinaryScalarType.STRING;
    }
}
