package io.nop.rpc.model.proto;

import io.nop.commons.type.BinaryScalarType;
import io.nop.core.unittest.BaseTestCase;
import io.nop.rpc.model.ApiMessageFieldModel;
import io.nop.rpc.model.ApiMessageModel;
import io.nop.rpc.model.ApiModel;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestProtoParserMapAndScalars extends BaseTestCase {

    static final String PROTO = """
            syntax = "proto3";
            package demo;

            message DemoMsg {
              int32 count = 1;
              bool enabled = 2;
              bytes payload = 3;
              int64 total = 4;
              string name = 5;
              map<string, int32> tags = 6;
            }
            """;

    @Test
    public void testScalarTypesRecognized() {
        assertEquals(BinaryScalarType.INT32, BinaryScalarType.fromText("int32"));
        assertEquals(BinaryScalarType.INT64, BinaryScalarType.fromText("int64"));
        assertEquals(BinaryScalarType.BOOL, BinaryScalarType.fromText("bool"));
        assertEquals(BinaryScalarType.BYTES, BinaryScalarType.fromText("bytes"));
    }

    @Test
    public void testParseScalarFieldsAndMap() {
        ApiModel model = new ProtoFileParser().parseFromText(null, PROTO);

        ApiMessageModel message = model.getMessage("DemoMsg");
        assertNotNull(message);

        ApiMessageFieldModel count = message.getField("count");
        assertNotNull(count);
        assertSame(BinaryScalarType.INT32, count.getBinaryScalarType());
        assertTrue(count.getType().isAssignableTo(Integer.class) || count.getType().isAssignableTo(Integer.TYPE),
                "int32 should map to java int/Integer, got " + count.getType());

        ApiMessageFieldModel total = message.getField("total");
        assertSame(BinaryScalarType.INT64, total.getBinaryScalarType());

        // map 字段应能解析成功并映射为 Map 泛型
        ApiMessageFieldModel tags = message.getField("tags");
        assertNotNull(tags);
        assertTrue(tags.getType().getTypeName().contains("Map"),
                "map field should become a java Map type, got " + tags.getType().getTypeName());
    }
}
