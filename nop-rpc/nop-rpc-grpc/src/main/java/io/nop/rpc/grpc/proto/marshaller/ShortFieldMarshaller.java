package io.nop.rpc.grpc.proto.marshaller;

import com.google.protobuf.CodedInputStream;
import com.google.protobuf.CodedOutputStream;
import io.nop.rpc.grpc.proto.IFieldMarshaller;

import java.io.IOException;

public class ShortFieldMarshaller implements IFieldMarshaller {
    public static ShortFieldMarshaller INSTANCE = new ShortFieldMarshaller();


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
        out.writeInt32(propId, ((Short) value).intValue());
    }

    @Override
    public void writeFieldNoTag(CodedOutputStream out, Object value) throws IOException {
        out.writeInt32NoTag(((Short) value).intValue());
    }

    @Override
    public int computeSize(int propId, Object value) {
        return CodedOutputStream.computeInt32Size(propId, ((Short) value).intValue());
    }

    @Override
    public int computeSizeNoTag(Object value) {
        return CodedOutputStream.computeInt32SizeNoTag(((Short) value).intValue());
    }

}
