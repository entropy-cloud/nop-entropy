package io.nop.rpc.grpc.proto.marshaller;

import com.google.protobuf.CodedInputStream;
import com.google.protobuf.CodedOutputStream;
import io.nop.rpc.grpc.proto.IFieldMarshaller;

import java.io.IOException;

public class StringFieldMarshaller implements IFieldMarshaller {
    public static StringFieldMarshaller INSTANCE = new StringFieldMarshaller();


    @Override
    public boolean isObject() {
        return false;
    }

    @Override
    public Object readField(CodedInputStream in) throws IOException {
        return in.readStringRequireUtf8();
    }

    @Override
    public void writeField(CodedOutputStream out, int propId, Object value) throws IOException {
        out.writeString(propId, (String) value);
    }

    @Override
    public int computeSize(int propId, Object value) {
        return CodedOutputStream.computeStringSize(propId, (String) value);
    }
}
