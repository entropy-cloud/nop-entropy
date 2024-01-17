package io.nop.rpc.grpc.proto;

import com.google.protobuf.CodedInputStream;
import com.google.protobuf.CodedOutputStream;
import io.nop.api.core.exceptions.NopException;
import io.nop.commons.collections.IntHashMap;
import io.nop.commons.collections.MapOfInt;
import io.nop.commons.util.CollectionHelper;
import io.nop.rpc.grpc.proto.marshaller.GenericMessageParser;

import java.io.IOException;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import static io.nop.rpc.grpc.GrpcErrors.ARG_NAME;
import static io.nop.rpc.grpc.GrpcErrors.ARG_PROP_ID;
import static io.nop.rpc.grpc.GrpcErrors.ERR_GRPC_FIELD_NAME_DUPLICATE;
import static io.nop.rpc.grpc.GrpcErrors.ERR_GRPC_FIELD_PROP_ID_DUPLICATE;
import static io.nop.rpc.grpc.GrpcErrors.ERR_GRPC_UNKNOWN_FIELD_FOR_NAME;
import static io.nop.rpc.grpc.GrpcErrors.ERR_GRPC_UNKNOWN_FIELD_FOR_PROP_ID;

public class GenericObjSchema implements IFieldMarshaller {
    private MapOfInt<GenericFieldSchema> fieldsByPropId;

    private Map<String, GenericFieldSchema> fieldsByName;

    private final GenericMessageParser parser = new GenericMessageParser(this);

    public MapOfInt<GenericFieldSchema> getFieldsByPropId() {
        return fieldsByPropId;
    }

    @Override
    public boolean isObject() {
        return true;
    }

    @Override
    public int computeSize(int propId, Object value) {
        int len = computeSizeNoTag(value);
        return CodedOutputStream.computeTagSize(propId) + CodedOutputStream.computeUInt32SizeNoTag(len) + len;
    }


    @Override
    public int computeSizeNoTag(Object value) {
        Map<String, Object> map = (Map<String, Object>) value;
        return ProtobufMarshallerHelper.computeSize(this, map);
    }

    public Map<String, Object> parseObject(CodedInputStream in) throws IOException {
        return ProtobufMarshallerHelper.parseObject(in, this);
    }

    public void writeObject(CodedOutputStream out, Object value) throws IOException {
        ProtobufMarshallerHelper.writeObject(out, this, (Map<String, Object>) value);
    }

    public byte[] toByteArray(Object value) {
        Map<String, Object> map = (Map<String, Object>) value;
        int size = ProtobufMarshallerHelper.computeSize(this, map);
        byte[] data = new byte[size];
        CodedOutputStream out = CodedOutputStream.newInstance(data);
        try {
            ProtobufMarshallerHelper.writeObject(out, this, map);
            out.flush();
        } catch (IOException e) {
            throw NopException.adapt(e);
        }
        return data;
    }

    @Override
    public Object readField(CodedInputStream in) throws IOException {
        return in.readMessage(parser, null).getObj();
    }

    @Override
    public void writeField(CodedOutputStream out, int propId, Object value) throws IOException {
        out.writeMessage(propId, new DynamicMessage(this, (Map<String, Object>) value));
    }

    @Override
    public void writeFieldNoTag(CodedOutputStream out, Object value) throws IOException {
        writeObject(out, value);
    }


    public void setFieldList(List<GenericFieldSchema> fields) {
        fields.sort(Comparator.comparing(GenericFieldSchema::getPropId));
        this.fieldsByPropId = buildFieldsByPropId(fields);
        this.fieldsByName = buildFieldsByName(fields);
        if (this.fieldsByName.size() != fields.size())
            throw new NopException(ERR_GRPC_FIELD_NAME_DUPLICATE);

        if (this.fieldsByPropId.size() != fields.size())
            throw new NopException(ERR_GRPC_FIELD_PROP_ID_DUPLICATE);
    }

    private MapOfInt<GenericFieldSchema> buildFieldsByPropId(List<GenericFieldSchema> fields) {
        int maxPropId = fields.get(fields.size() - 1).getPropId();
        MapOfInt<GenericFieldSchema> fieldsMap;
        if (maxPropId < 256) {
            fieldsMap = new IntHashMap<>(maxPropId + 1);
        } else {
            fieldsMap = new IntHashMap<>();
        }
        fields.forEach(field -> {
            fieldsMap.set(field.getPropId(), field);
        });

        return fieldsMap;
    }

    private Map<String, GenericFieldSchema> buildFieldsByName(List<GenericFieldSchema> fields) {
        Map<String, GenericFieldSchema> fieldsMap = CollectionHelper.newLinkedHashMap(fields.size());

        fields.forEach(field -> {
            fieldsMap.put(field.getName(), field);
        });

        return fieldsMap;
    }

    public GenericFieldSchema getFieldByPropId(int propId) {
        return fieldsByPropId.get(propId);
    }

    public GenericFieldSchema requireFieldByPropId(int propId) {
        GenericFieldSchema field = getFieldByPropId(propId);
        if (field == null)
            throw new NopException(ERR_GRPC_UNKNOWN_FIELD_FOR_PROP_ID)
                    .param(ARG_PROP_ID, propId);
        return field;
    }

    public GenericFieldSchema getFieldByName(String name) {
        return fieldsByName.get(name);
    }

    public GenericFieldSchema requireFieldByName(String name) {
        GenericFieldSchema field = getFieldByName(name);
        if (field == null)
            throw new NopException(ERR_GRPC_UNKNOWN_FIELD_FOR_NAME)
                    .param(ARG_NAME, name);
        return field;
    }


}
