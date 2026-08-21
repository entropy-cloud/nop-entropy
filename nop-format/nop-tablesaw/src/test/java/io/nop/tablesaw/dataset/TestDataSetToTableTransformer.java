/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.tablesaw.dataset;

import io.nop.commons.type.StdDataType;
import io.nop.dataset.IDataRow;
import io.nop.dataset.IDataSet;
import io.nop.dataset.IDataSetMeta;
import io.nop.dataset.impl.BaseDataFieldMeta;
import io.nop.dataset.impl.BaseDataSetMeta;
import io.nop.dataset.impl.MapDataRow;
import org.junit.jupiter.api.Test;
import tech.tablesaw.api.DoubleColumn;
import tech.tablesaw.api.IntColumn;
import tech.tablesaw.api.StringColumn;
import tech.tablesaw.api.Table;

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestDataSetToTableTransformer {

    static class ListDataSet implements IDataSet {
        private final List<IDataRow> rows;
        private final IDataSetMeta meta;
        private int index;

        ListDataSet(IDataSetMeta meta, List<IDataRow> rows) {
            this.meta = meta;
            this.rows = rows;
        }

        @Override
        public IDataSetMeta getMeta() {
            return meta;
        }

        @Override
        public boolean isDetached() {
            return true;
        }

        @Override
        public IDataSet detach() {
            return this;
        }

        @Override
        public boolean hasNext() {
            return index < rows.size();
        }

        @Override
        public IDataRow next() {
            return rows.get(index++);
        }

        @Override
        public long getReadCount() {
            return index;
        }

        @Override
        public Iterator<IDataRow> iterator() {
            return this;
        }

        @Override
        public void close() {
        }
    }

    static IDataSet buildDataSet() {
        BaseDataFieldMeta nameMeta = BaseDataFieldMeta.build("name", StdDataType.STRING);
        BaseDataFieldMeta ageMeta = BaseDataFieldMeta.build("age", StdDataType.INT);
        BaseDataFieldMeta scoreMeta = BaseDataFieldMeta.build("score", StdDataType.DOUBLE);
        BaseDataSetMeta meta = new BaseDataSetMeta(Arrays.asList(nameMeta, ageMeta, scoreMeta));

        List<IDataRow> rows = Arrays.asList(
                row(meta, "Alice", 30, 90.5),
                row(meta, "Bob", null, null),
                row(meta, null, 25, 70.0));
        return new ListDataSet(meta, rows);
    }

    static MapDataRow row(BaseDataSetMeta meta, Object... values) {
        MapDataRow row = new MapDataRow(meta);
        for (int i = 0; i < values.length; i++) {
            row.setObject(i, values[i]);
        }
        return row;
    }

    @Test
    public void testTransformKeepsValues() {
        Table table = new DataSetToTableTransformer("test").apply(buildDataSet());

        assertEquals(3, table.rowCount());

        StringColumn name = table.stringColumn("name");
        assertEquals("Alice", name.get(0));
        assertEquals("Bob", name.get(1));

        IntColumn age = table.intColumn("age");
        assertEquals(30, age.get(0));
        assertEquals(25, age.get(2));

        DoubleColumn score = table.doubleColumn("score");
        assertEquals(90.5, score.get(0), 1e-9);
        assertEquals(70.0, score.get(2), 1e-9);

        // null 单元格应变为 missing，非空单元格不应丢失
        assertTrue(age.isMissing(1));
        assertTrue(name.isMissing(2));
        assertTrue(score.isMissing(1));
    }
}
