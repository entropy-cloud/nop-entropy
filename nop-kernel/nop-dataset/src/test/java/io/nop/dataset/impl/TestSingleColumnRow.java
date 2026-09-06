package io.nop.dataset.impl;

import io.nop.api.core.exceptions.NopException;
import io.nop.commons.type.StdDataType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestSingleColumnRow {

    private static BaseDataSetMeta metaOf(String name) {
        return new BaseDataSetMeta(List.of(BaseDataFieldMeta.build(name, StdDataType.ANY)));
    }

    @Test
    public void testSetObjectOnReadonlyRowThrows() {
        SingleColumnRow row = new SingleColumnRow(metaOf("value"), "v");

        // 修复前：setObject 为空实现，静默丢弃写入，调用方得到假成功
        assertThrows(NopException.class, () -> row.setObject(0, "other"));
    }

    @Test
    public void testReadSemanticsUnchanged() {
        SingleColumnRow row = new SingleColumnRow(metaOf("value"), "v");
        assertEquals("v", row.getObject(0));
        assertNull(row.getObject(1));
        assertEquals(1, row.getFieldCount());
        assertTrue(row.isReadonly());
    }
}
