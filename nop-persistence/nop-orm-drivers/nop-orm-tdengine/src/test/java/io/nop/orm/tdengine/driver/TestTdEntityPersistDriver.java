/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.tdengine.driver;

import io.nop.api.core.exceptions.NopException;
import io.nop.core.lang.sql.SQL;
import io.nop.dao.dialect.IDialect;
import io.nop.dao.jdbc.IJdbcTemplate;
import io.nop.orm.IOrmEntity;
import io.nop.orm.model.IEntityModel;
import io.nop.orm.persister.IBatchAction;
import io.nop.orm.session.IOrmSessionImplementor;
import io.nop.orm.tdengine.model.TdTableMeta;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

public class TestTdEntityPersistDriver {

    private TdEntityPersistDriver driver;
    private IJdbcTemplate jdbcTemplate;
    private IEntityModel entityModel;
    private final AtomicReference<SQL> capturedSql = new AtomicReference<>();

    @BeforeEach
    public void setUp() throws Exception {
        driver = new TdEntityPersistDriver();
        jdbcTemplate = mock(IJdbcTemplate.class);
        entityModel = mock(IEntityModel.class);

        lenient().when(entityModel.getName()).thenReturn("EntityTd");
        lenient().when(entityModel.getTableName()).thenReturn("td_table");
        lenient().when(entityModel.getQuerySpace()).thenReturn("td");
        lenient().doReturn(Collections.emptyList()).when(entityModel).getColumns();
        lenient().doReturn(Collections.emptyList()).when(entityModel).getPkColumns();

        IDialect dialect = mock(IDialect.class);
        lenient().when(dialect.normalizeTableName(any())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(dialect.isUseAsInFrom()).thenReturn(false);

        lenient().when(jdbcTemplate.executeUpdate(any(SQL.class))).thenAnswer(inv -> {
            capturedSql.set(inv.getArgument(0, SQL.class));
            return 0L;
        });
        lenient().when(jdbcTemplate.findLong(any(SQL.class), any())).thenAnswer(inv -> {
            capturedSql.set(inv.getArgument(0, SQL.class));
            return 0L;
        });

        setField("entityModel", entityModel);
        setField("tableMeta", new TdTableMeta(entityModel));
        setField("jdbcTemplate", jdbcTemplate);
        setField("dialect", dialect);
        setField("querySpace", "td");
        setField("binders", new io.nop.dataset.binder.IDataParameterBinder[0]);
    }

    private void setField(String name, Object value) throws Exception {
        Field field = TdEntityPersistDriver.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(driver, value);
    }

    @Test
    public void testDeleteByExampleSetsQuerySpace() {
        IOrmEntity example = mock(IOrmEntity.class);
        driver.deleteByExample(null, example, mock(IOrmSessionImplementor.class));

        // 修复前 querySpace 为 null，删除会打到默认数据源
        assertEquals("td", capturedSql.get().getQuerySpace());
    }

    @Test
    public void testCountByExampleSetsQuerySpace() {
        IOrmEntity example = mock(IOrmEntity.class);
        driver.countByExample(null, example, mock(IOrmSessionImplementor.class));

        assertEquals("td", capturedSql.get().getQuerySpace());
    }

    @Test
    public void testBatchExecuteAsyncRejectsUpdateActions() {
        IBatchAction.EntityUpdateAction updateAction = mock(IBatchAction.EntityUpdateAction.class);
        NopException e = assertThrows(NopException.class,
                () -> driver.batchExecuteAsync(true, "td", null, List.of(updateAction), null,
                        mock(IOrmSessionImplementor.class)));
        assertEquals("nop.err.orm.tdengine.update-not-supported", e.getErrorCode());
    }

    @Test
    public void testUpdateByExampleThrows() {
        // 修复前静默返回 0，属无信号数据丢失
        NopException e = assertThrows(NopException.class,
                () -> driver.updateByExample(null, mock(IOrmEntity.class), mock(IOrmEntity.class),
                        mock(IOrmSessionImplementor.class)));
        assertEquals("nop.err.orm.tdengine.update-not-supported", e.getErrorCode());
    }
}
