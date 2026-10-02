/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.api.core.json;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestJsonSchema {

    @Test
    public void testDefaultsAreNull() {
        JsonSchema schema = new JsonSchema();
        assertNull(schema.getId());
        assertNull(schema.getTitle());
        assertNull(schema.getDescription());
        assertNull(schema.getType());
        assertNull(schema.getProperties());
        assertNull(schema.getRequired());
        assertNull(schema.getItems());
        assertNull(schema.getAdditionalProperties());
        assertNull(schema.getEnum());
        assertNull(schema.getDefault());
        assertNull(schema.getFormat());
        assertNull(schema.getMinimum());
        assertNull(schema.getMaximum());
        assertNull(schema.getMinLength());
        assertNull(schema.getMaxLength());
        assertNull(schema.getPattern());
    }

    @Test
    public void testScalarFieldRoundTrip() {
        JsonSchema schema = new JsonSchema();
        schema.setId("https://example.com/a");
        schema.setTitle("标题");
        schema.setDescription("desc");
        schema.setType("string");
        schema.setFormat("date-time");
        schema.setPattern("^[a-z]+$");
        schema.setMinLength(1);
        schema.setMaxLength(10);
        schema.setMinimum(3);
        schema.setMaximum(100);
        schema.setAdditionalProperties(Boolean.FALSE);

        assertEquals("https://example.com/a", schema.getId());
        assertEquals("标题", schema.getTitle());
        assertEquals("desc", schema.getDescription());
        assertEquals("string", schema.getType());
        assertEquals("date-time", schema.getFormat());
        assertEquals("^[a-z]+$", schema.getPattern());
        assertEquals(1, schema.getMinLength());
        assertEquals(10, schema.getMaxLength());
        assertEquals(3, schema.getMinimum());
        assertEquals(100, schema.getMaximum());
        assertEquals(Boolean.FALSE, schema.getAdditionalProperties());
    }

    @Test
    public void testEnumAndDefaultValues() {
        JsonSchema schema = new JsonSchema();
        schema.setEnum(Arrays.asList("A", "B", "C"));
        schema.setDefault("A");
        assertEquals(Arrays.asList("A", "B", "C"), schema.getEnum());
        assertEquals("A", schema.getDefault());
    }

    @Test
    public void testNestedObjectAndArrayStructure() {
        JsonSchema objectSchema = new JsonSchema();
        objectSchema.setType("object");

        JsonSchema nameProp = new JsonSchema();
        nameProp.setType("string");
        Map<String, JsonSchema> props = new LinkedHashMap<>();
        props.put("name", nameProp);
        objectSchema.setProperties(props);
        Set<String> required = new LinkedHashSet<>();
        required.add("name");
        objectSchema.setRequired(required);

        assertSame(nameProp, objectSchema.getProperties().get("name"));
        assertTrue(objectSchema.getRequired().contains("name"));

        // 数组类型：items 描述元素结构
        JsonSchema arraySchema = new JsonSchema();
        arraySchema.setType("array");
        JsonSchema items = new JsonSchema();
        items.setType("integer");
        arraySchema.setItems(items);
        assertSame(items, arraySchema.getItems());
        assertEquals("integer", arraySchema.getItems().getType());
    }
}
