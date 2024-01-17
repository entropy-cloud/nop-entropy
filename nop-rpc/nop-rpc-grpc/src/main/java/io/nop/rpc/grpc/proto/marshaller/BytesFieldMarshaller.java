package io.nop.rpc.grpc.proto.marshaller;

import com.google.protobuf.CodedInputStream;
import com.google.protobuf.CodedOutputStream;
import io.nop.rpc.grpc.proto.IFieldMarshaller;

import java.io.IOException;

public class BytesFieldMarshaller implements IFieldMarshaller {
    public static BytesFieldMarshaller INSTANCE = new BytesFieldMarshaller();


    @Override
    public boolean isObject() {
        return false;
    }

    @Override
    public Object readField(CodedInputStream in) throws IOException {
        return in.readByteArray();
    }

    @Override
    public void writeField(CodedOutputStream out, int propId, Object value) throws IOException {
        out.writeByteArray(propId, (byte[]) value);
    }

    @Override
    public void writeFieldNoTag(CodedOutputStream out, Object value) throws IOException {
        out.writeByteArrayNoTag((byte[]) value);
    }

    @Override
    public int computeSize(int propId, Object value) {
        return CodedOutputStream.computeByteArraySize(propId, (byte[]) value);
    }

    @Override
    public int computeSizeNoTag(Object value) {
        return CodedOutputStream.computeByteArraySizeNoTag((byte[]) value);
    }
}
