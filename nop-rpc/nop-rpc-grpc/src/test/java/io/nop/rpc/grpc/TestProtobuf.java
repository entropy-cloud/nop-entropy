package io.nop.rpc.grpc;

import com.google.protobuf.CodedInputStream;
import com.google.protobuf.DescriptorProtos;
import io.nop.api.core.exceptions.NopException;
import io.nop.commons.util.StringHelper;
import io.nop.rpc.grpc.proto.GenericFieldSchema;
import io.nop.rpc.grpc.proto.GenericObjSchema;
import io.nop.rpc.grpc.proto.marshaller.IntFieldMarshaller;
import io.nop.rpc.grpc.proto.marshaller.StringFieldMarshaller;
import io.nop.rpc.grpc.test.Message;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestProtobuf {
    @Test
    public void testParse() {
        Message.InnerMessage inner = Message.InnerMessage.newBuilder()
                .setAge(3).setName("abc").build();
        Message.OuterMessage msg = Message.OuterMessage.newBuilder().setInner(inner).addInnerList(inner).build();
        byte[] bytes = msg.toByteArray();
        System.out.println(StringHelper.bytesToHex(bytes));

        try {
            msg = Message.OuterMessage.parseFrom(bytes);
            assertEquals(3, msg.getInner().getAge());
            assertEquals("abc", msg.getInnerList(0).getName());
        } catch (Exception e) {
            throw NopException.adapt(e);
        }
    }

    @Test
    public void testDynamicParser() throws IOException {
        Message.InnerMessage msg = Message.InnerMessage.newBuilder()
                .setName("abc").setAge(3).build();

        byte[] data = msg.toByteArray();
        GenericObjSchema innerSchema = buildInnerSchema();
        Map<String, Object> ret = innerSchema.parseObject(CodedInputStream.newInstance(data));
        assertEquals("abc", ret.get("name"));
        assertEquals(3, ret.get("age"));
    }

    @Test
    public void testFullParser() {
        Message.InnerMessage inner = Message.InnerMessage.newBuilder()
                .setName("abc").setAge(3).build();

        Message.OuterMessage out = Message.OuterMessage.newBuilder()
                .addInnerList(inner).addValue(4).build();

        byte[] data = out.toByteArray();

        try {
            Message.OuterMessage.parseFrom(data);
        } catch (Exception e) {
            throw NopException.adapt(e);
        }

        try {
            Map<String, Object> ret = buildOuterSchema().parseObject(CodedInputStream.newInstance(data));
            List<Map<String, Object>> list = (List<Map<String, Object>>) ret.get("inner_list");
            assertEquals(1, list.size());
            assertEquals("abc", list.get(0).get("name"));
            assertEquals("[4]", ret.get("value").toString());
        } catch (Exception e) {
            throw NopException.adapt(e);
        }
    }

    @Test
    public void testSerialize(){
        Message.InnerMessage inner = Message.InnerMessage.newBuilder()
                .setName("abc").setAge(3).build();

        Message.OuterMessage out = Message.OuterMessage.newBuilder()
                .addInnerList(inner).addValue(4).build();

        byte[] data = out.toByteArray();

        try {
            Map<String, Object> ret = buildOuterSchema().parseObject(CodedInputStream.newInstance(data));
            List<Map<String, Object>> list = (List<Map<String, Object>>) ret.get("inner_list");
            assertEquals(1, list.size());
            assertEquals("abc", list.get(0).get("name"));
            assertEquals("[4]", ret.get("value").toString());

            data = buildOuterSchema().toByteArray(ret);

            out = Message.OuterMessage.parseFrom(data);
            assertEquals(4, out.getValue(0));
            assertEquals(3, out.getInnerList(0).getAge());
        } catch (Exception e) {
            throw NopException.adapt(e);
        }
    }

    private GenericObjSchema buildOuterSchema() {
        GenericObjSchema schema = new GenericObjSchema();

        GenericFieldSchema inner = new GenericFieldSchema(1, "inner",
                DescriptorProtos.FieldDescriptorProto.Label.LABEL_OPTIONAL,
                buildInnerSchema());

        GenericFieldSchema inner_list = new GenericFieldSchema(2, "inner_list",
                DescriptorProtos.FieldDescriptorProto.Label.LABEL_REPEATED,
                buildInnerSchema());

        GenericFieldSchema value = new GenericFieldSchema(3, "value",
                DescriptorProtos.FieldDescriptorProto.Label.LABEL_REPEATED,
                IntFieldMarshaller.INSTANCE);

        schema.setFieldList(Arrays.asList(inner, inner_list, value));
        return schema;
    }

    private GenericObjSchema buildInnerSchema() {
        GenericObjSchema schema = new GenericObjSchema();
        GenericFieldSchema name = new GenericFieldSchema(1, "name",
                DescriptorProtos.FieldDescriptorProto.Label.LABEL_OPTIONAL,
                StringFieldMarshaller.INSTANCE);

        GenericFieldSchema age = new GenericFieldSchema(2, "age",
                DescriptorProtos.FieldDescriptorProto.Label.LABEL_OPTIONAL,
                IntFieldMarshaller.INSTANCE);

        schema.setFieldList(Arrays.asList(name, age));
        return schema;
    }
}
