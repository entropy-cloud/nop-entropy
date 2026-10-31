/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.data.source;

import io.nop.core.initialize.CoreInitialization;
import io.nop.core.lang.sql.SQL;
import io.nop.dao.dialect.IDialect;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import io.nop.api.core.beans.query.QueryBean;
import io.nop.orm.IOrmSession;
import io.nop.orm.IOrmEntity;
import io.nop.orm.IOrmSessionFactory;
import io.nop.orm.impl.OrmTemplateImpl;
import io.nop.orm.model.IEntityModel;
import io.nop.orm.model.IEntityPropModel;
import io.nop.orm.model.IColumnModel;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * plan 2517 R2-14 红⑦（C2-005，C10-006）回归钉：抢占谓词 NULL 退化消除——穿透谓词生成层。
 * <p>缺陷态：新任务行抢占字段初始 NULL 不进 WHERE（currentAcquireValues 逐字段跳过 null），
 * UPDATE 退化为按 id 无条件更新 → 两消费者均匹配成功、同一任务重复投递，且静默无日志 → 红；
 * 修复后：源值为 null 的抢占字段生成 {@code col IS NULL} 进 WHERE，NULL 初始态参与 CAS → 绿。
 * 钉穿透谓词生成层：直接断言条件 UPDATE 的 SQL 文本（session mock 屏蔽面消解）。</p>
 * <p>既有非 NULL 冲突钉（TestDaoEntityBlockingSourceClaim#testClaimConflictSkipsItem 等）保持零回退。</p>
 */
public class TestDaoEntityBlockingSourceClaimCra2517 {

    private DaoEntityBlockingSource<io.nop.orm.IOrmEntity> source;
    private IEntityDao<io.nop.orm.IOrmEntity> dao;
    private IOrmSession session;
    private final AtomicReference<SQL> capturedSql = new AtomicReference<>();

    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    @BeforeEach
    @SuppressWarnings({"unchecked", "rawtypes"})
    public void setUp() {
        source = new DaoEntityBlockingSource<>();
        source.setEntityName("io.test.EntityQNull");
        source.setAcquiredStatusField("status");
        source.setAcquiredStatus(1);
        source.setAcquireHostField("hostField");
        source.setQueryBuilder(ctx -> new QueryBean());

        dao = (IEntityDao<IOrmEntity>) mock(IEntityDao.class);
        IDaoProvider daoProvider = mock(IDaoProvider.class);
        lenient().when(daoProvider.<IOrmEntity>dao("io.test.EntityQNull")).thenReturn(dao);
        source.setDaoProvider(daoProvider);

        capturedSql.set(null);
        session = mock(IOrmSession.class);
        lenient().when(session.executeUpdate(any(SQL.class))).thenAnswer(inv -> {
            capturedSql.set(inv.getArgument(0));
            return 1L; // 本消费者条件 UPDATE 匹配成功
        });

        IOrmSessionFactory sessionFactory = mock(IOrmSessionFactory.class);
        IDialect dialect = mock(IDialect.class);
        lenient().when(dialect.getUpdateKeyword()).thenReturn("update");
        lenient().when(sessionFactory.getDialectForQuerySpace(any())).thenReturn(dialect);
        lenient().when(sessionFactory.openSession(anyBoolean())).thenReturn(session);
        source.setOrmTemplate(new OrmTemplateImpl(sessionFactory));
    }

    private io.nop.orm.IOrmEntity entityWithNullOldValues() {
        io.nop.orm.IOrmEntity entity = mock(io.nop.orm.IOrmEntity.class);
        IEntityModel entityModel = mock(IEntityModel.class);
        IEntityPropModel idProp = mock(IEntityPropModel.class);
        when(entityModel.getIdProp()).thenReturn(idProp);
        when(idProp.getName()).thenReturn("id");
        lenient().when(idProp.isSingleColumn()).thenReturn(true);
        lenient().when(idProp.getColumnPropId()).thenReturn(1);
        IColumnModel idCol = mock(IColumnModel.class);
        lenient().when(idCol.getName()).thenReturn("id");
        lenient().when(idCol.getCode()).thenReturn("ID");
        lenient().when(idCol.getPropId()).thenReturn(1);
        lenient().when(entityModel.getPkColumns()).thenAnswer(inv -> java.util.Collections.singletonList(idCol));
        lenient().when(entityModel.getQuerySpace()).thenReturn("default");
        lenient().when(entityModel.getTableName()).thenReturn("ENTITY_Q");
        lenient().when(entityModel.isUseTenant()).thenReturn(false);
        lenient().when(entityModel.getColumn(any(), anyBoolean())).thenAnswer(inv -> {
            IColumnModel col = mock(IColumnModel.class);
            when(col.getCode()).thenReturn(((String) inv.getArgument(0)).toUpperCase());
            return col;
        });
        lenient().when(entity.orm_entityModel()).thenReturn(entityModel);
        lenient().when(entity.get_id()).thenReturn("q1");
        // 新任务行：抢占字段旧值均为 NULL（缺陷态退化为无条件 UPDATE 的输入形态）
        lenient().when(entity.orm_propValueByName("status")).thenReturn(null);
        lenient().when(entity.orm_propValueByName("hostField")).thenReturn(null);
        return entity;
    }

    /**
     * 红⑦：NULL 旧值必须生成 col IS NULL 谓词（SQL 文本断言，穿透谓词生成层）。
     */
    @Test
    public void testCra2517_C2_005_nullOldValueGeneratesIsNullPredicate() throws InterruptedException {
        io.nop.orm.IOrmEntity entity = entityWithNullOldValues();
        when(dao.findAllByQuery(any(QueryBean.class))).thenReturn(List.of(entity));
        lenient().when(dao.newEntity()).thenAnswer(inv -> mock(io.nop.orm.IOrmEntity.class));

        List<Object> c = new ArrayList<>();
        int n = source.drainTo(c, 1, 0, 0);

        assertEquals(1, n, "NULL 初始行抢占必须成功");
        SQL sql = capturedSql.get();
        assertNotNull(sql, "缺陷态：updateByExample 路径（session mock 屏蔽谓词生成层）无显式条件 UPDATE 文本 → 红");
        String text = sql.getText();
        assertTrue(text.contains("is null"), "NULL 旧值必须生成 IS NULL 谓词，实际 SQL: " + text);
        assertTrue(text.contains("HOSTFIELD is null") || text.contains("hostField is null"),
                "hostField 旧值 NULL 必须生成 IS NULL，实际 SQL: " + text);
        assertTrue(text.contains("STATUS is null") || text.contains("status is null"),
                "status 旧值 NULL 必须生成 IS NULL，实际 SQL: " + text);
        // 主键条件仍在（三字段全 NULL 时 UPDATE 仍带主键 + IS NULL 条件）
        assertTrue(text.toLowerCase().contains("where"), "必须带 WHERE 条件: " + text);
    }

    /**
     * 非 NULL 旧值维持等值谓词（与非 NULL 钉同语义，混合路径口径验证）。
     */
    @Test
    public void testCra2517_C2_005_nonNullOldValueKeepsEqPredicate() throws InterruptedException {
        io.nop.orm.IOrmEntity entity = entityWithNullOldValues();
        // 全部抢占字段旧值非 null：维持既有等值谓词路径（与非 NULL 钉同语义）
        lenient().when(entity.orm_propValueByName("status")).thenReturn(0);
        lenient().when(entity.orm_propValueByName("hostField")).thenReturn("hostA");
        when(dao.findAllByQuery(any(QueryBean.class))).thenReturn(List.of(entity));
        lenient().when(dao.newEntity()).thenAnswer(inv -> mock(io.nop.orm.IOrmEntity.class));
        lenient().when(session.updateByExample(any(), any())).thenReturn(1L);

        List<Object> c = new ArrayList<>();
        int n = source.drainTo(c, 1, 0, 0);

        assertEquals(1, n);
        org.mockito.Mockito.verify(session).updateByExample(any(), any());
    }
}
