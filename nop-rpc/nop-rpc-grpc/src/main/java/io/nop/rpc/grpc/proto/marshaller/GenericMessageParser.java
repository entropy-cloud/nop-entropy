package io.nop.rpc.grpc.proto.marshaller;

import com.google.protobuf.CodedInputStream;
import com.google.protobuf.ExtensionRegistryLite;
import com.google.protobuf.InvalidProtocolBufferException;
import io.nop.api.core.exceptions.NopException;
import io.nop.rpc.grpc.proto.DynamicMessage;
import io.nop.rpc.grpc.proto.GenericObjSchema;
import io.nop.rpc.grpc.proto.ProtobufMarshallerHelper;

import java.io.IOException;

public class GenericMessageParser extends AbstractMessageParser<DynamicMessage> {
    private final GenericObjSchema schema;

    public GenericMessageParser(GenericObjSchema schema) {
        this.schema = schema;
    }

    @Override
    public DynamicMessage parsePartialFrom(CodedInputStream input, ExtensionRegistryLite extensionRegistry) throws InvalidProtocolBufferException {
        try {
            return new DynamicMessage(schema, ProtobufMarshallerHelper.parseObject(input, schema));
        } catch (IOException e) {
            throw NopException.adapt(e);
        }
    }
}
