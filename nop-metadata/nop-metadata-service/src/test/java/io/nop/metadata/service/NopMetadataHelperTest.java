package io.nop.metadata.service;

import io.nop.api.core.exceptions.NopException;
import io.nop.metadata.dao.entity.NopMetaEntity;
import io.nop.metadata.dao.entity.NopMetaEntityField;
import io.nop.metadata.dao.entity.NopMetaTable;
import io.nop.search.api.SearchableDoc;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class NopMetadataHelperTest {

    @Test
    public void testTruncateNull() {
        assertEquals("", NopMetadataHelper.truncate(null, 10));
    }

    @Test
    public void testTruncateShort() {
        assertEquals("abc", NopMetadataHelper.truncate("abc", 10));
    }

    @Test
    public void testTruncateExact() {
        assertEquals("1234567890", NopMetadataHelper.truncate("1234567890", 10));
    }

    @Test
    public void testTruncateLong() {
        assertEquals("12345", NopMetadataHelper.truncate("1234567890", 5));
    }

    @Test
    public void testJoinNullParts() {
        assertEquals("a b", NopMetadataHelper.join(" ", "a", null, "b"));
    }

    @Test
    public void testJoinEmptyParts() {
        assertEquals("a b", NopMetadataHelper.join(" ", "a", "", "b"));
    }

    @Test
    public void testJoinAllNull() {
        assertEquals("", NopMetadataHelper.join(",", null, null));
    }

    @Test
    public void testStringOfNull() {
        assertNull(NopMetadataHelper.stringOf(new HashMap<>(), "missing"));
    }

    @Test
    public void testStringOfPresent() {
        Map<String, Object> data = new HashMap<>();
        data.put("key", "value");
        assertEquals("value", NopMetadataHelper.stringOf(data, "key"));
    }

    @Test
    public void testStringOfNumeric() {
        Map<String, Object> data = new HashMap<>();
        data.put("key", 42);
        assertEquals("42", NopMetadataHelper.stringOf(data, "key"));
    }

    @Test
    public void testToErrorMessageHasMessage() {
        Exception e = new Exception("test error");
        assertEquals("test error", NopMetadataHelper.toErrorMessage(e));
    }

    @Test
    public void testToErrorMessageNullMessage() {
        Exception e = new Exception((String) null);
        assertEquals("java.lang.Exception", NopMetadataHelper.toErrorMessage(e));
    }

    @Test
    public void testToErrorMessageNopException() {
        Exception e = new NopException(NopMetadataErrors.ERR_QUALITY_RULE_NOT_FOUND) {
        };
        String msg = NopMetadataHelper.toErrorMessage(e);
        assertTrue(msg.contains("quality-rule-not-found") || msg.contains("ERR_QUALITY_RULE_NOT_FOUND"));
    }

    @Test
    public void testToSearchableDocMetaEntity() {
        NopMetaEntity entity = new NopMetaEntity();
        entity.setMetaEntityId("e1");
        entity.setEntityName("test.Entity");
        entity.setDisplayName("Test Entity");
        SearchableDoc doc = NopMetadataHelper.toSearchableDoc(entity);
        assertEquals("e1", doc.getId());
        assertEquals("test.Entity", doc.getName());
        assertEquals("Test Entity", doc.getTitle());
    }

    @Test
    public void testToSearchableDocMetaTable() {
        NopMetaTable table = new NopMetaTable();
        table.setMetaTableId("t1");
        table.setTableName("test_table");
        table.setDisplayName("Test Table");
        SearchableDoc doc = NopMetadataHelper.toSearchableDoc(table);
        assertEquals("t1", doc.getId());
        assertEquals("test_table", doc.getName());
        assertEquals("Test Table", doc.getTitle());
    }

    @Test
    public void testToSearchableDocMetaEntityField() {
        NopMetaEntityField field = new NopMetaEntityField();
        field.setEntityFieldId("f1");
        field.setFieldName("testField");
        field.setDisplayName("Test Field");
        SearchableDoc doc = NopMetadataHelper.toSearchableDoc(field);
        assertEquals("f1", doc.getId());
        assertEquals("testField", doc.getName());
        assertEquals("Test Field", doc.getTitle());
    }
}
