/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.tdengine.model;

import io.nop.commons.collections.IntArray;
import io.nop.commons.collections.MutableIntArray;
import io.nop.core.lang.sql.SQL;
import io.nop.orm.IOrmEntity;
import io.nop.orm.model.IColumnModel;
import io.nop.orm.model.IEntityModel;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

public class TestTdSqlHelper {

    private final IColumnModel tsCol = col(1, "ts", "ts");
    private final IColumnModel nbrCol = col(2, "nbr", "nbr");
    private final IColumnModel locationCol = col(3, "location", "location");
    private final IColumnModel valueCol = col(4, "value", "value");

    private TdTableMeta tableMeta() {
        IEntityModel entityModel = mock(IEntityModel.class);
        List<IColumnModel> columns = Arrays.asList(tsCol, nbrCol, locationCol, valueCol);
        lenient().doReturn(columns).when(entityModel).getColumns();
        lenient().doReturn(tsCol).when(entityModel).requireColumnByTag("ts");
        lenient().doReturn(nbrCol).when(entityModel).requireColumnByTag("nbr");
        lenient().when(entityModel.getDbSchema()).thenReturn("");
        lenient().when(entityModel.getTableName()).thenReturn("meters");
        lenient().when(locationCol.containsTag("tag")).thenReturn(true);
        return new TdTableMeta(entityModel);
    }

    private IOrmEntity entity(Map<Integer, Object> values) {
        IOrmEntity entity = mock(IOrmEntity.class);
        lenient().when(entity.orm_propValue(1)).thenReturn(values.get(1));
        lenient().when(entity.orm_propValue(2)).thenReturn(values.get(2));
        lenient().when(entity.orm_propValue(3)).thenReturn(values.get(3));
        lenient().when(entity.orm_propValue(4)).thenReturn(values.get(4));
        return entity;
    }

    static IColumnModel col(int propId, String name, String code) {
        IColumnModel col = mock(IColumnModel.class);
        lenient().when(col.getPropId()).thenReturn(propId);
        lenient().when(col.getName()).thenReturn(name);
        lenient().when(col.getCode()).thenReturn(code);
        return col;
    }

    @Test
    public void testGenInsertSubTableSqlTagValues() {
        TdTableMeta tableMeta = tableMeta();
        IOrmEntity entity = entity(Map.of(2, "dev1", 3, "factory-A"));

        SQL sql = TdSqlHelper.genInsertSubTableSql(SQL.begin(), tableMeta, entity).end();
        String text = sql.toString();

        // 子表名 + 超级表名
        assertTrue(text.contains("dev1"), text);
        assertTrue(text.contains("USING db0.meters"), text);
        // tag 列名
        assertTrue(text.contains("(location)"), text);
        // TAGS 值必须是实体的属性值，而不是列模型对象的 toString
        assertTrue(text.contains("TAGS ('factory-A')"), text);
        assertFalse(text.contains("OrmColumnModel"), text);
    }

    @Test
    public void testGenBatchInsertSubTableSqlValues() {
        TdTableMeta tableMeta = tableMeta();
        IOrmEntity e1 = entity(Map.of(1, 1700000000000L, 2, "dev1", 3, "factory-A", 4, 1.5));
        IOrmEntity e2 = entity(Map.of(1, 1700000001000L, 2, "dev1", 3, "factory-A", 4, 2.5));

        SQL sql = TdSqlHelper.genBatchInsertSubTableSql(SQL.begin(), tableMeta, Arrays.asList(e1, e2)).end();
        String text = sql.toString();

        // 第一行带 VALUES 关键字，后续行只有括号；值来自实体属性（ts + 数据列，tag 列不进入 VALUES）
        assertTrue(text.contains("VALUES(1700000000000,1.5)"), text);
        assertTrue(text.contains("(1700000001000,2.5)"), text);
        // 修复前恒生成空 VALUES() 且无后续行
        assertFalse(text.contains("VALUES()"), text);
    }

    @Test
    public void testGenLoadSqlContainsFromAndAndSeparator() {
        TdTableMeta tableMeta = tableMeta();
        IEntityModel entityModel = tableMeta.getEntityModel();
        lenient().when(entityModel.getColumnByPropId(1, false)).thenReturn(tsCol);
        lenient().when(entityModel.getColumnByPropId(4, false)).thenReturn(valueCol);
        lenient().doReturn(Arrays.asList(tsCol, nbrCol)).when(entityModel).getPkColumns();

        IOrmEntity entity = entity(Map.of(1, 1700000000000L, 2, "dev1"));
        IntArray propIds = MutableIntArray.of(1, 4);

        SQL sql = TdSqlHelper.genLoadSql(tableMeta, entity, propIds).end();
        String text = sql.toString();

        // 修复前缺少 from 子句
        assertTrue(text.contains("from db0.meters"), text);
        // 复合主键条件之间必须是 AND，修复前为逗号
        assertTrue(text.contains("ts=1700000000000 and nbr='dev1'"), text);
        assertFalse(text.contains("ts=1700000000000,nbr="), text);
    }
}
