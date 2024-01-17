package io.nop.rpc.grpc.proto.marshaller;

import com.google.protobuf.CodedInputStream;
import com.google.protobuf.CodedOutputStream;
import io.nop.rpc.grpc.proto.IFieldMarshaller;

import java.io.IOException;

public class IntFieldMarshaller implements IFieldMarshaller {
    public static IntFieldMarshaller INSTANCE = new IntFieldMarshaller();


    @Override
    public boolean isObject() {
        return false;
    }

    @Override
    public Object readField(CodedInputStream in) throws IOException {
        return in.readInt32();
    }

    @Override
    public void writeField(CodedOutputStream out, int propId, Object value) throws IOException {
        out.writeInt32(propId, (Integer) value);
    }

    @Override
    public void writeFieldNoTag(CodedOutputStream out, Object value) throws IOException {
        out.writeInt32NoTag((Integer) value);
    }

    @Override
    public int computeSize(int propId, Object value) {
        return CodedOutputStream.computeInt32Size(propId, (Integer) value);
    }

    @Override
    public int computeSizeNoTag(Object value) {
        return CodedOutputStream.computeInt32SizeNoTag((Integer) value);
    }
}
