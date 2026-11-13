/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.tdengine.driver;

import io.nop.api.core.config.AppConfig;
import io.nop.api.core.config.IConfigReference;
import io.nop.commons.util.StringHelper;
import io.nop.core.lang.sql.SQL;
import io.nop.dao.dialect.IDialect;
import io.nop.dao.jdbc.IJdbcTemplate;
import io.nop.orm.IOrmEntity;
import io.nop.orm.model.IColumnModel;
import io.nop.orm.model.IEntityModel;
import io.nop.orm.persister.IBatchAction;
import io.nop.orm.session.IOrmSessionImplementor;
import io.nop.orm.tdengine.TdEngineConfigs;
import io.nop.orm.tdengine.model.TdTableMeta;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

/**
 * 2.5-MA-11（plan 2554 裁定 D）：TDengine 批写三因子 chunk 化载体。
 * 断言目标：超上界批拆分为多条 SQL（行数因子）、子表声明/字节因子参与切分、
 * 同子表 tag 分歧显式留痕（checkTagDivergence 返回检测面）且保持取首行行为。
 */
public class TestTdBatchChunking {

    private static final int PROP_TS = 1;
    private static final int PROP_NBR = 2;
    private static final int PROP_DTYPE = 3;

    private TdEntityPersistDriver driver;
    private IJdbcTemplate jdbcTemplate;
    private IEntityModel entityModel;
    private final List<SQL> executedSqls = new ArrayList<>();
    private final List<IConfigReference<?>> configOverrides = new ArrayList<>();

    @BeforeEach
    public void setUp() throws Exception {
        driver = new TdEntityPersistDriver();
        jdbcTemplate = mock(IJdbcTemplate.class);
        entityModel = mock(IEntityModel.class);

        IColumnModel tsCol = column("ts", PROP_TS);
        IColumnModel nbrCol = column("nbr", PROP_NBR);
        IColumnModel dtypeCol = column("dtype", PROP_DTYPE);
        lenient().when(dtypeCol.containsTag("tag")).thenReturn(true);

        lenient().when(entityModel.getName()).thenReturn("EntityTd");
        lenient().when(entityModel.getTableName()).thenReturn("td_table");
        lenient().when(entityModel.getQuerySpace()).thenReturn("td");
        lenient().when(entityModel.getDbSchema()).thenReturn(null);
        lenient().when(entityModel.requireColumnByTag("ts")).thenReturn(tsCol);
        lenient().when(entityModel.requireColumnByTag("nbr")).thenReturn(nbrCol);
        lenient().doReturn(List.of(tsCol, nbrCol, dtypeCol)).when(entityModel).getColumns();
        lenient().doReturn(Collections.emptyList()).when(entityModel).getPkColumns();

        IDialect dialect = mock(IDialect.class);
        lenient().when(dialect.normalizeTableName(any())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(dialect.isUseAsInFrom()).thenReturn(false);

        lenient().when(jdbcTemplate.executeUpdate(any(SQL.class))).thenAnswer(inv -> {
            executedSqls.add(inv.getArgument(0, SQL.class));
            return 0L;
        });

        setField("entityModel", entityModel);
        setField("tableMeta", new TdTableMeta(entityModel));
        setField("jdbcTemplate", jdbcTemplate);
        setField("dialect", dialect);
        setField("querySpace", "td");
        setField("binders", new io.nop.dataset.binder.IDataParameterBinder[0]);
    }

    private static IColumnModel column(String name, int propId) {
        IColumnModel col = mock(IColumnModel.class);
        lenient().when(col.getName()).thenReturn(name);
        lenient().when(col.getCode()).thenReturn(name);
        lenient().when(col.getPropId()).thenReturn(propId);
        return col;
    }

    private void setField(String name, Object value) throws Exception {
        Field field = TdEntityPersistDriver.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(driver, value);
    }

    private void overrideConfig(IConfigReference<Integer> ref, int value) {
        AppConfig.getConfigProvider().updateConfigValue(ref, value);
        configOverrides.add(ref);
    }

    @AfterEach
    public void tearDown() {
        // 恢复缺省配置，避免污染其他用例
        for (IConfigReference<?> ref : configOverrides) {
            AppConfig.getConfigProvider().updateConfigValue(ref, null);
        }
    }

    private IOrmEntity entity(long ts, String nbr, String dtype) {
        IOrmEntity entity = mock(IOrmEntity.class);
        lenient().when(entity.orm_propValue(PROP_TS)).thenReturn(ts);
        lenient().when(entity.orm_propValue(PROP_NBR)).thenReturn(nbr);
        lenient().when(entity.orm_propValue(PROP_DTYPE)).thenReturn(dtype);
        return entity;
    }

    private List<IBatchAction.EntitySaveAction> saveActions(IOrmEntity... entities) {
        List<IBatchAction.EntitySaveAction> actions = new ArrayList<>();
        for (IOrmEntity entity : entities) {
            IBatchAction.EntitySaveAction action = mock(IBatchAction.EntitySaveAction.class);
            lenient().when(action.getEntity()).thenReturn(entity);
            actions.add(action);
        }
        return actions;
    }

    private void executeInsert(List<IBatchAction.EntitySaveAction> actions) {
        CompletableFuture<Void> future = driver
                .batchExecuteAsync(true, "td", actions, null, null, mock(IOrmSessionImplementor.class))
                .toCompletableFuture();
        // 2553 守卫不变式：返回 future 同步完成
        assertTrue(future.isDone(), "batchExecuteAsync 返回 future 必须同步完成");
        future.join();
    }

    private static int countRows(SQL sql) {
        return StringHelper.countMatches(sql.getText(), "\n(") + 1;
    }

    @Test
    public void testBatchExceedingMaxRowsSplitIntoChunks() {
        overrideConfig(TdEngineConfigs.CFG_TD_BATCH_MAX_ROWS, 2);

        List<IBatchAction.EntitySaveAction> actions = saveActions(
                entity(1, "dev1", "a"), entity(2, "dev1", "a"),
                entity(3, "dev1", "a"), entity(4, "dev1", "a"), entity(5, "dev1", "a"));
        executeInsert(actions);

        assertEquals(3, executedSqls.size(), "5 行批、max-rows=2 应拆为 2+2+1 三个 chunk（三条 SQL）");
        assertEquals(2, countRows(executedSqls.get(0)));
        assertEquals(2, countRows(executedSqls.get(1)));
        assertEquals(1, countRows(executedSqls.get(2)));
    }

    @Test
    public void testSubTableDeclarationFactorSplitsChunks() {
        overrideConfig(TdEngineConfigs.CFG_TD_BATCH_MAX_ROWS, 2);

        // 两个子表（dev1/dev2），各 2 行：chunk 边界落在子表组边界，每个 chunk 只含一个子表声明
        List<IBatchAction.EntitySaveAction> actions = saveActions(
                entity(1, "dev1", "a"), entity(2, "dev1", "a"),
                entity(3, "dev2", "b"), entity(4, "dev2", "b"));
        executeInsert(actions);

        assertEquals(2, executedSqls.size());
        assertTrue(executedSqls.get(0).getText().contains("dev1"));
        assertFalse(executedSqls.get(0).getText().contains("dev2"), "chunk 内子表声明必须唯一");
        assertTrue(executedSqls.get(1).getText().contains("dev2"));
        assertFalse(executedSqls.get(1).getText().contains("dev1"));
    }

    @Test
    public void testByteFactorSplitsBeforeRowLimit() {
        overrideConfig(TdEngineConfigs.CFG_TD_BATCH_MAX_ROWS, 100);
        // 极小字节上界：行数远未到 100 即触发切分（字节因子生效）
        overrideConfig(TdEngineConfigs.CFG_TD_BATCH_MAX_BYTES, 256);

        List<IBatchAction.EntitySaveAction> actions = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            actions.addAll(saveActions(entity(i, "dev" + i % 2, "tag-value-" + i)));
        }
        executeInsert(actions);

        assertTrue(executedSqls.size() > 1, "字节上界应先于行数上界触发切分");
        for (SQL sql : executedSqls) {
            assertTrue(sql.getText().length() <= 512,
                    "每个 chunk SQL 长度应在字节上界量级内: " + sql.getText().length());
        }
    }

    @Test
    public void testTagDivergenceDetectedFirstRowWins() {
        overrideConfig(TdEngineConfigs.CFG_TD_BATCH_MAX_ROWS, 10);

        // 同子表两行 tag（dtype）分歧：缺陷态静默取首行，修复后显式检测留痕、行为仍取首行
        List<IBatchAction.EntitySaveAction> actions = saveActions(
                entity(1, "dev1", "a"), entity(2, "dev1", "b"));
        executeInsert(actions);

        assertEquals(1, executedSqls.size());
        // 行为保持：TAGS 声明取首行 tag 值
        String sqlText = executedSqls.get(0).getText();
        assertTrue(sqlText.contains("TAGS ('a')"), "TAGS 声明应取首行 tag 值: " + sqlText);
        assertFalse(sqlText.contains("'b'"), "次行 tag 值不得进入 TAGS 声明: " + sqlText);

        // 检测面：分歧被显式检出（WARN 留痕经 checkTagDivergence，行为面钉检测返回值）
        List<IOrmEntity> divergent = List.of(entity(1, "dev1", "a"), entity(2, "dev1", "b"));
        assertTrue(driver.checkTagDivergence("dev1", divergent), "tag 分歧必须被检测到");

        List<IOrmEntity> consistent = List.of(entity(1, "dev1", "a"), entity(2, "dev1", "a"));
        assertFalse(driver.checkTagDivergence("dev1", consistent), "tag 一致不得误报");

        AtomicReference<Boolean> single = new AtomicReference<>();
        // 单行组无分歧可言
        single.set(driver.checkTagDivergence("dev1", List.of(entity(1, "dev1", "a"))));
        assertFalse(single.get());
    }
}
