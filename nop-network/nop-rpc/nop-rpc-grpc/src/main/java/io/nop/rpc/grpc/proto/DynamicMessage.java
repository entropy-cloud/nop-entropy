/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.rpc.grpc.proto;

import com.google.protobuf.AbstractMessage;
import com.google.protobuf.CodedOutputStream;
import com.google.protobuf.Descriptors;
import com.google.protobuf.Message;
import com.google.protobuf.Parser;
import com.google.protobuf.UnknownFieldSet;

import java.io.IOException;
import java.util.Map;

public class DynamicMessage extends AbstractMessage {
    private final GenericObjSchema schema;
    private final Map<String, Object> obj;

    private final UnknownFieldSet unknownFields = UnknownFieldSet.getDefaultInstance();

    public DynamicMessage(GenericObjSchema schema, Map<String, Object> obj) {
        this.schema = schema;
        this.obj = obj;
    }

    public Map<String, Object> getObj() {
        return obj;
    }

    @Override
    public int getSerializedSize() {
        return ProtobufMarshallerHelper.computeSize(schema, obj);
    }

    @Override
    public void writeTo(CodedOutputStream output) throws IOException {
        ProtobufMarshallerHelper.writeObject(output, schema, obj);
    }

    @Override
    public Parser<? extends Message> getParserForType() {
        return null;
    }

    @Override
    public Message.Builder newBuilderForType() {
        return null;
    }

    @Override
    public Message.Builder toBuilder() {
        return null;
    }

    @Override
    public Message getDefaultInstanceForType() {
        return null;
    }

    @Override
    public Descriptors.Descriptor getDescriptorForType() {
        return null;
    }

    @Override
    public Map<Descriptors.FieldDescriptor, Object> getAllFields() {
        return null;
    }

    @Override
    public boolean hasField(Descriptors.FieldDescriptor field) {
        return false;
    }

    @Override
    public Object getField(Descriptors.FieldDescriptor field) {
        return null;
    }

    @Override
    public int getRepeatedFieldCount(Descriptors.FieldDescriptor field) {
        return 0;
    }

    @Override
    public Object getRepeatedField(Descriptors.FieldDescriptor field, int index) {
        return null;
    }


    @Override
    public UnknownFieldSet getUnknownFields() {
        return unknownFields;
    }
}
