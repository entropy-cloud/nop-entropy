package io.nop.rpc.grpc.proto;

import com.google.protobuf.CodedInputStream;
import com.google.protobuf.CodedOutputStream;

import java.io.IOException;

public interface IFieldMarshaller {
    boolean isObject();

    Object readField(CodedInputStream in) throws IOException;

    void writeField(CodedOutputStream out, int propId, Object value) throws IOException;

    void writeFieldNoTag(CodedOutputStream out, Object value) throws IOException;

    int computeSize(int propId, Object value);

    int computeSizeNoTag(Object value);
}