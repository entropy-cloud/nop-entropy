package io.nop.rpc.grpc.proto.marshaller;

import io.grpc.MethodDescriptor;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

public class EmptyMarshaller implements MethodDescriptor.Marshaller<Object> {
    public static final EmptyMarshaller INSTANCE = new EmptyMarshaller();

    @Override
    public InputStream stream(Object value) {
        return new ByteArrayInputStream(new byte[0]);
    }

    @Override
    public Object parse(InputStream stream) {
        return null;
    }
}
