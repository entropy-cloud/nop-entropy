/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.flow.builder;

import io.nop.api.core.exceptions.NopException;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.lang.xml.XNode;
import io.nop.core.resource.IResource;
import io.nop.core.resource.VirtualFileSystem;
import io.nop.stream.core.common.typeinfo.BasicTypeInfo;
import io.nop.stream.core.exceptions.StreamException;
import io.nop.stream.flow.model.StreamModel;
import io.nop.xlang.xdsl.DslModelParser;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WI8b: the <schemas> declaration surface is consumed at build time — fields resolve
 * to BasicTypeInfo via the managed-name parser (StreamSchemaRegistry), the field coder
 * is the built-in SimpleTypeSerializer, and unknown type names fail fast on first
 * error with the field anchored.
 */
public class TestStreamSchemaConsumer {

    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    private static StreamModel parse(String schemasXml) {
        String xml = "<stream xmlns:x=\"/nop/schema/xdsl.xdef\" "
                + "x:schema=\"/nop/schema/stream/stream.xdef\" "
                + "name=\"schema-consumer\" version=\"1\">"
                + schemasXml

                + "</stream>";
        XNode node = XNode.parse(xml);
        IResource resource = VirtualFileSystem.instance().getResource("/nop/schema/stream/stream.xdef");
        assertTrue(resource.exists());
        return (StreamModel) new DslModelParser().parseFromNode(node);
    }

    @Test
    public void testResolveSchemaFieldSpecs() {
        StreamModel model = parse("<schemas><schema id=\"s1\"><fields>"
                + "<field name=\"a\" type=\"string\"/>"
                + "<field name=\"b\" type=\"bigint\" nullable=\"true\"/>"
                + "<field name=\"c\" type=\"double\" defaultValue=\"1.5\"/>"
                + "</fields></schema></schemas>");
        StreamModelDslBuilder builder = StreamModelDslBuilder.of(model, new InMemoryBeanFunctionResolver());
        builder.build();

        StreamSchemaRegistry registry = builder.schemaRegistry();
        assertTrue(registry.contains("s1"));
        assertEquals(3, registry.resolveSchema("s1").size());

        StreamSchemaRegistry.FieldSpec a = registry.resolveSchema("s1").get(0);
        assertEquals("a", a.getName());
        assertSame(BasicTypeInfo.STRING, a.getType());
        assertFalse(Boolean.TRUE.equals(a.getNullable()));

        StreamSchemaRegistry.FieldSpec b = registry.resolveSchema("s1").get(1);
        assertSame(BasicTypeInfo.LONG, b.getType());
        assertEquals(Boolean.TRUE, b.getNullable());

        StreamSchemaRegistry.FieldSpec c = registry.resolveSchema("s1").get(2);
        assertSame(BasicTypeInfo.DOUBLE, c.getType());
        assertEquals("1.5", String.valueOf(c.getDefaultValue()));
    }

    @Test
    public void testManagedNameIdentityGolden() {
        // 九名 identity golden：受管名 -> BasicTypeInfo 单例 + D7 §1.3 类型映射
        assertSame(BasicTypeInfo.STRING, StreamSchemaRegistry.resolveManagedType("string"));
        assertSame(BasicTypeInfo.INT, StreamSchemaRegistry.resolveManagedType("int"));
        assertSame(BasicTypeInfo.LONG, StreamSchemaRegistry.resolveManagedType("bigint"));
        assertSame(BasicTypeInfo.SHORT, StreamSchemaRegistry.resolveManagedType("smallint"));
        assertSame(BasicTypeInfo.BYTE, StreamSchemaRegistry.resolveManagedType("tinyint"));
        assertSame(BasicTypeInfo.FLOAT, StreamSchemaRegistry.resolveManagedType("float"));
        assertSame(BasicTypeInfo.DOUBLE, StreamSchemaRegistry.resolveManagedType("double"));
        assertSame(BasicTypeInfo.BOOLEAN, StreamSchemaRegistry.resolveManagedType("boolean"));
        assertSame(BasicTypeInfo.BYTE_ARRAY, StreamSchemaRegistry.resolveManagedType("bytes"));

        assertEquals(String.class, StreamSchemaRegistry.resolveManagedType("string").getTypeClass());
        assertEquals(Long.class, StreamSchemaRegistry.resolveManagedType("bigint").getTypeClass());
        assertEquals(byte[].class, StreamSchemaRegistry.resolveManagedType("bytes").getTypeClass());
        // 内建 serializer 即字段 coder
        assertNotNull(StreamSchemaRegistry.resolveManagedType("string").getSerializer());
    }

    @Test
    public void testUnknownTypeFailsFastWithFieldAnchored() {
        StreamModel model = parse("<schemas><schema id=\"s1\"><fields>"
                + "<field name=\"a\" type=\"string\"/>"
                + "<field name=\"b\" type=\"varchar2\"/>"
                + "</fields></schema></schemas>");
        StreamException ex = assertThrows(StreamException.class,
                () -> StreamModelDslBuilder.of(model, new InMemoryBeanFunctionResolver()).build());
        assertEquals("nop.err.stream.invalid-arg", ex.getErrorCode().toString());
        String detail = String.valueOf(ex.getParams().get("detail"));
        assertTrue(detail.contains("schema=s1"), detail);
        assertTrue(detail.contains("field=b"), detail);
        assertTrue(detail.contains("type=varchar2"), detail);
    }

    @Test
    public void testUnknownSchemaIdThrows() {
        StreamModel model = parse("<schemas><schema id=\"s1\"><fields>"
                + "<field name=\"a\" type=\"string\"/>"
                + "</fields></schema></schemas>");
        StreamModelDslBuilder builder = StreamModelDslBuilder.of(model, new InMemoryBeanFunctionResolver());
        builder.build();
        NopException ex = assertThrows(NopException.class, () -> builder.schemaRegistry().resolveSchema("nope"));
        assertEquals("nop.err.stream.invalid-arg", ex.getErrorCode().toString());
    }

    @Test
    public void testResolveSchemaBeforeBuildThrows() {
        StreamModel model = parse("<schemas><schema id=\"s1\"><fields>"
                + "<field name=\"a\" type=\"string\"/>"
                + "</fields></schema></schemas>");
        StreamModelDslBuilder builder = StreamModelDslBuilder.of(model, new InMemoryBeanFunctionResolver());
        assertThrows(IllegalStateException.class, builder::schemaRegistry);
    }
}
