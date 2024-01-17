package io.nop.rpc.grpc.proto.marshaller;

import com.google.protobuf.CodedInputStream;
import com.google.protobuf.CodedOutputStream;
import io.nop.rpc.grpc.proto.IFieldMarshaller;

import java.io.IOException;

public class DoubleFieldMarshaller implements IFieldMarshaller {
    public static DoubleFieldMarshaller INSTANCE = new DoubleFieldMarshaller();

    @Override
    public String getGrpcTypeName(){
        return "double";
    }

    @Override
    public boolean isObject() {
        return false;
    }

    @Override
    public Object readField(CodedInputStream in) throws IOException {
        return in.readDouble();
    }

    @Override
    public void writeField(CodedOutputStream out, int propId, Object value) throws IOException {
        out.writeDouble(propId, (Double) value);
    }

    @Override
    public void writeFieldNoTag(CodedOutputStream out, Object value) throws IOException {
        out.writeDoubleNoTag((Double) value);
    }

    @Override
    public int computeSize(int propId, Object value) {
        return CodedOutputStream.computeDoubleSize(propId, (Double) value);
    }

    @Override
    public int computeSizeNoTag(Object value) {
        return CodedOutputStream.computeDoubleSizeNoTag((Double) value);
    }
}
