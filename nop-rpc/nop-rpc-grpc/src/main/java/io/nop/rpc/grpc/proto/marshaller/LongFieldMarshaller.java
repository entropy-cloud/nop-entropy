package io.nop.rpc.grpc.proto.marshaller;

import com.google.protobuf.CodedInputStream;
import com.google.protobuf.CodedOutputStream;
import io.nop.rpc.grpc.proto.IFieldMarshaller;

import java.io.IOException;

public class LongFieldMarshaller implements IFieldMarshaller {
    public static LongFieldMarshaller INSTANCE = new LongFieldMarshaller();


    @Override
    public boolean isObject() {
        return false;
    }

    @Override
    public Object readField(CodedInputStream in) throws IOException {
        return in.readInt64();
    }

    @Override
    public void writeField(CodedOutputStream out, int propId, Object value) throws IOException {
        out.writeInt64(propId, (Long) value);
    }

    @Override
    public int computeSize(int propId, Object value) {
        return CodedOutputStream.computeInt64Size(propId, (Long) value);
    }
}
