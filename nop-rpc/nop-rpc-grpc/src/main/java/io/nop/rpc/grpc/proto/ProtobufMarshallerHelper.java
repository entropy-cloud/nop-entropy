package io.nop.rpc.grpc.proto;

import com.google.protobuf.CodedInputStream;
import com.google.protobuf.CodedOutputStream;
import io.nop.api.core.exceptions.NopException;
import io.nop.commons.type.StdDataType;
import io.nop.rpc.grpc.proto.marshaller.BooleanFieldMarshaller;
import io.nop.rpc.grpc.proto.marshaller.ByteStringFieldMarshaller;
import io.nop.rpc.grpc.proto.marshaller.DoubleFieldMarshaller;
import io.nop.rpc.grpc.proto.marshaller.FloatFieldMarshaller;
import io.nop.rpc.grpc.proto.marshaller.IntFieldMarshaller;
import io.nop.rpc.grpc.proto.marshaller.JsonFieldMarshaller;
import io.nop.rpc.grpc.proto.marshaller.LocalDateFieldMarshaller;
import io.nop.rpc.grpc.proto.marshaller.LocalDateTimeFieldMarshaller;
import io.nop.rpc.grpc.proto.marshaller.LongFieldMarshaller;
import io.nop.rpc.grpc.proto.marshaller.ShortFieldMarshaller;
import io.nop.rpc.grpc.proto.marshaller.StringFieldMarshaller;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.google.protobuf.CodedOutputStream.computeInt32SizeNoTag;
import static com.google.protobuf.WireFormat.WIRETYPE_LENGTH_DELIMITED;
import static com.google.protobuf.WireFormat.getTagFieldNumber;
import static io.nop.rpc.grpc.GrpcErrors.ARG_DATA_TYPE;
import static io.nop.rpc.grpc.GrpcErrors.ERR_GRPC_NOT_SUPPORT_DATA_TYPE;

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
                List<Object> list = (List<Object>) value;
                if (!list.isEmpty()) {
                    IFieldMarshaller marshaller = field.getMarshaller();
                    if (field.isObject()) {
                        for (Object o : list) {
                            marshaller.writeField(out, field.getPropId(), o);
                        }
                    } else {
                        out.writeTag(field.getPropId(), WIRETYPE_LENGTH_DELIMITED);
                        out.writeInt32NoTag(list.size());
                        for (Object o : list) {
                            marshaller.writeFieldNoTag(out, o);
                        }
                    }
                }
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

                if (!list.isEmpty()) {
                    if (field.isObject()) {
                        for (Object item : list) {
                            size += field.computeSize(item);
                        }
                    } else {
                        size += 1;
                        size += computeInt32SizeNoTag(list.size());
                        for (Object item : list) {
                            size += field.computeSizeNoTag(item);
                        }
                    }
                }
            } else {
                size += field.computeSize(propValue);
            }
        }
        return size;
    }

    public static IFieldMarshaller getMarshallerForType(StdDataType dataType) {
        switch (dataType) {
            case STRING:
                return StringFieldMarshaller.INSTANCE;
            case INT:
                return IntFieldMarshaller.INSTANCE;
            case SHORT:
                return ShortFieldMarshaller.INSTANCE;
            case LONG:
                return LongFieldMarshaller.INSTANCE;
            case DATE:
                return LocalDateFieldMarshaller.INSTANCE;
            case DATETIME:
                return LocalDateTimeFieldMarshaller.INSTANCE;
            case FLOAT:
                return FloatFieldMarshaller.INSTANCE;
            case DOUBLE:
                return DoubleFieldMarshaller.INSTANCE;
            case BOOLEAN:
                return BooleanFieldMarshaller.INSTANCE;
            case BYTES:
                return ByteStringFieldMarshaller.INSTANCE;
            case MAP:
            case ANY:
                return JsonFieldMarshaller.INSTANCE;
            default:
                throw new NopException(ERR_GRPC_NOT_SUPPORT_DATA_TYPE)
                        .param(ARG_DATA_TYPE, dataType);
        }
    }
}
