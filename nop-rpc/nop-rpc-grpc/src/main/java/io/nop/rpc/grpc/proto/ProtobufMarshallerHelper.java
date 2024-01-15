package io.nop.rpc.grpc.proto;

import com.google.protobuf.CodedInputStream;
import com.google.protobuf.CodedOutputStream;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.google.protobuf.WireFormat.getTagFieldNumber;

public class ProtobufMarshallerHelper {
    public static Map<String, Object> parseObject(CodedInputStream in, GenericObjSchema schema) throws IOException {
        Map<String, Object> ret = new LinkedHashMap<>();

        do {
            int tag = in.readTag();
            if (tag <= 0)
                break;

            int propId = getTagFieldNumber(tag);
            GenericFieldSchema fieldSchema = schema.requireFieldByPropId(propId);
            if (fieldSchema.isRepeated()) {
                if (fieldSchema.isObject()) {
                    Object value = fieldSchema.getMarshaller().readField(in);
                    List<Object> list = (List<Object>) ret.get(fieldSchema.getName());
                    if (list == null) {
                        list = new ArrayList<>();
                        ret.put(fieldSchema.getName(), list);
                    }
                    list.add(value);
                } else {
                    int length = in.readRawVarint32();
                    List<Object> value = new ArrayList<>();
                    int limit = in.pushLimit(length);
                    while (in.getBytesUntilLimit() > 0) {
                        value.add(fieldSchema.getMarshaller().readField(in));
                    }
                    in.popLimit(limit);
                    ret.put(fieldSchema.getName(), value);
                }
            } else {
                Object value = fieldSchema.getMarshaller().readField(in);
                ret.put(fieldSchema.getName(), value);
            }
        } while (true);

        in.checkLastTagWas(0);

        return ret;
    }

    public static void writeObject(CodedOutputStream out, GenericObjSchema schema, Map<String, Object> map) throws IOException {
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            String name = entry.getKey();
            Object value = entry.getValue();
            if (value == null)
                continue;

            GenericFieldSchema field = schema.requireFieldByName(name);
            if (field.isRepeated()) {

            } else {
                field.getMarshaller().writeField(out, field.getPropId(), value);
            }
        }
    }

    public static int computeSize(GenericObjSchema schema, Map<String, Object> map) {
        int size = 0;
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            String propName = entry.getKey();
            Object propValue = entry.getValue();
            if (propValue == null)
                continue;

            GenericFieldSchema field = schema.requireFieldByName(propName);
            if (field.isRepeated()) {
                List<Object> list = (List<Object>) propValue;
                for (Object item : list) {
                    size += field.computeSize(item);
                }
            } else {
                size += field.computeSize(propValue);
            }
        }
        return size;
    }
}
