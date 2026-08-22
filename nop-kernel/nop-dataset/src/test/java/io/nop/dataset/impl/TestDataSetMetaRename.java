package io.nop.dataset.impl;

import io.nop.commons.type.StdDataType;
import io.nop.dataset.IDataSetMeta;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestDataSetMetaRename {

    private static BaseDataSetMeta metaOf(String... names) {
        List<BaseDataFieldMeta> cols = new java.util.ArrayList<>();
        for (String name : names) {
            cols.add(BaseDataFieldMeta.build(name, StdDataType.ANY));
        }
        return new BaseDataSetMeta(cols);
    }

    @Test
    public void testProjectWithRenameRenamesColumns() {
        BaseDataSetMeta meta = metaOf("a", "b", "c");
        Map<String, String> new2old = new LinkedHashMap<>();
        new2old.put("x", "a");

        // 修复前：newName 被丢弃，产出 meta 仍报旧列名 a，重命名静默失效
        IDataSetMeta projected = meta.projectWithRename(new2old);
        assertEquals(1, projected.getFieldCount());
        assertEquals("x", projected.getFieldName(0));
        assertEquals(0, projected.getFieldIndex("x"));
    }

    @Test
    public void testRenameKeepsUnrenamedColumns() {
        BaseDataSetMeta meta = metaOf("a", "b", "c");
        Map<String, String> new2old = new LinkedHashMap<>();
        new2old.put("x", "a");

        IDataSetMeta renamed = meta.rename(new2old);
        assertEquals(3, renamed.getFieldCount());
        // 未重命名的列保留原名（列顺序跟随 Map 迭代序，断言与顺序无关）
        assertTrue(renamed.hasField("b"));
        assertTrue(renamed.hasField("c"));
        // 重命名生效：新名存在且可定位，旧名不可见
        assertTrue(renamed.hasField("x"));
        assertTrue(renamed.getFieldIndex("x") >= 0);
        assertFalse(renamed.hasField("a"));
    }

    @Test
    public void testProjectMetaHasFieldRespectsProjection() {
        BaseDataSetMeta source = metaOf("a", "b", "c");
        IDataSetMeta projected = source.project(List.of("a", "c"));

        // 修复前：hasField 委托 source，被投影掉的 b 返回 true
        assertTrue(projected.hasField("a"));
        assertFalse(projected.hasField("b"));
        assertTrue(projected.hasField("c"));
        // hasField=true 的字段 getFieldIndex 必须 >= 0，否则调用方拿 -1 索引越界
        assertTrue(projected.getFieldIndex("a") >= 0);
        assertTrue(projected.getFieldIndex("c") >= 0);
    }

    @Test
    public void testProjectMetaProjectWithRename() {
        BaseDataSetMeta source = metaOf("a", "b", "c");
        IDataSetMeta projected = source.project(List.of("a", "c"));

        Map<String, String> new2old = new LinkedHashMap<>();
        new2old.put("x", "a");

        // 修复前：内层 projectWithRename 产出旧名 meta，外层用新名 keySet 建索引，
        // 真实重命名场景构造即抛 ERR_DATASET_UNKNOWN_COLUMN
        IDataSetMeta renamed = projected.projectWithRename(new2old);
        assertEquals(1, renamed.getFieldCount());
        assertEquals("x", renamed.getFieldName(0));
        assertTrue(renamed.hasField("x"));
    }
}
