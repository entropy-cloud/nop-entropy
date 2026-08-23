/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.tdengine.model;

import io.nop.api.core.exceptions.NopException;
import io.nop.orm.IOrmEntity;
import io.nop.orm.model.IColumnModel;
import io.nop.orm.model.IEntityModel;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class TestTdTableMeta {

    private TdTableMeta tableMeta() {
        IEntityModel entityModel = mock(IEntityModel.class);
        IColumnModel tsCol = TestTdSqlHelper.col(1, "ts", "ts");
        IColumnModel nbrCol = TestTdSqlHelper.col(2, "nbr", "nbr");
        lenient().doReturn(Arrays.asList(tsCol, nbrCol)).when(entityModel).getColumns();
        when(entityModel.requireColumnByTag("ts")).thenReturn(tsCol);
        when(entityModel.requireColumnByTag("nbr")).thenReturn(nbrCol);
        lenient().when(entityModel.getDbSchema()).thenReturn("db0");
        lenient().when(entityModel.getTableName()).thenReturn("meters");
        return new TdTableMeta(entityModel);
    }

    private IOrmEntity entityWithNbr(Object nbr) {
        IOrmEntity entity = mock(IOrmEntity.class);
        lenient().when(entity.orm_propValue(2)).thenReturn(nbr);
        return entity;
    }

    @Test
    public void testSubTableNameFromNumber() {
        assertEquals("dev101", tableMeta().getSubTableName(entityWithNbr(101)));
    }

    @Test
    public void testSubTableNameFromValidString() {
        assertEquals("meter_01", tableMeta().getSubTableName(entityWithNbr("meter_01")));
    }

    @Test
    public void testSubTableNameRejectsInvalidIdentifier() {
        // 子表名直接拼接进 INSERT 语句且无法参数化，非法标识符必须拒绝
        NopException e = assertThrows(NopException.class,
                () -> tableMeta().getSubTableName(entityWithNbr("bad name'")));
        assertEquals("nop.err.orm.tdengine.invalid-sub-table-name", e.getErrorCode());
    }

    @Test
    public void testSubTableNameRejectsNull() {
        assertThrows(NopException.class, () -> tableMeta().getSubTableName(entityWithNbr(null)));
    }
}
