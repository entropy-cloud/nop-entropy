/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.data.source;

import io.nop.api.core.beans.query.QueryBean;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import io.nop.orm.IOrmEntity;
import io.nop.orm.impl.OrmTemplateImpl;
import io.nop.orm.IOrmSession;
import io.nop.orm.IOrmSessionFactory;
import io.nop.orm.model.IEntityModel;
import io.nop.orm.model.IEntityPropModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.nop.core.initialize.CoreInitialization;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 回归覆盖审查报告 DATA-01：抢占（acquire）必须通过条件UPDATE原子完成。
 * 修复前SELECT到实体后仅在内存中修改占用字段并随session flush写回，
 * 两个消费者SELECT到同一批记录时会各自成功写入占用标记，同一任务被重复投递。
 * <p>
 * 条件UPDATE影响0行表示另一消费者已抢占，该记录必须被跳过。
 */
public class TestDaoEntityBlockingSourceClaim {

    private DaoEntityBlockingSource<IOrmEntity> source;
    private IEntityDao<IOrmEntity> dao;
    private IOrmSession session;

    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    @SuppressWarnings("unchecked")
    @BeforeEach
    public void setUp() {
        source = new DaoEntityBlockingSource<>();
        source.setEntityName("io.test.EntityQ");
        // 抢占字段配置
        source.setAcquiredStatusField("status");
        source.setAcquiredStatus(1);
        source.setAcquireHostField("hostField");
        source.setQueryBuilder(ctx -> new QueryBean());

        dao = (IEntityDao<IOrmEntity>) mock(IEntityDao.class);
        IDaoProvider daoProvider = mock(IDaoProvider.class);
        lenient().when(daoProvider.<IOrmEntity>dao("io.test.EntityQ")).thenReturn(dao);
        source.setDaoProvider(daoProvider);

        // 会话mock：updateByExample返回0表示条件更新未命中（另一消费者已抢占）
        session = mock(IOrmSession.class);
        lenient().when(session.updateByExample(any(), any())).thenReturn(0L);

        IOrmSessionFactory sessionFactory = mock(IOrmSessionFactory.class);
        lenient().when(sessionFactory.openSession(anyBoolean())).thenReturn(session);
        source.setOrmTemplate(new OrmTemplateImpl(sessionFactory));
    }

    private void returnItems(IOrmEntity entity) {
        when(dao.findAllByQuery(any(QueryBean.class))).thenReturn(List.of(entity));
        lenient().when(dao.newEntity()).thenAnswer(inv -> mock(IOrmEntity.class));

        IEntityModel entityModel = mock(IEntityModel.class);
        IEntityPropModel idProp = mock(IEntityPropModel.class);
        when(entityModel.getIdProp()).thenReturn(idProp);
        when(idProp.isSingleColumn()).thenReturn(true);
        lenient().when(entity.orm_entityModel()).thenReturn(entityModel);
        lenient().when(entity.get_id()).thenReturn("q1");
    }

    @Test
    public void testClaimConflictSkipsItem() throws InterruptedException {
        IOrmEntity entity = mock(IOrmEntity.class);
        returnItems(entity);
        // 记录被选中时的旧状态值
        lenient().when(entity.orm_propValueByName("status")).thenReturn(0);

        List<Object> c = new ArrayList<>();
        int n = source.drainTo(c, 1, 0, 0);

        // 条件UPDATE未命中（已被其他消费者抢占）时该记录必须被跳过
        assertEquals(0, n, "抢占失败的记录不应被投递");
        assertEquals(0, c.size());
    }

    @Test
    public void testClaimSuccessDeliversItemAndAppliesAcquireValues() throws InterruptedException {
        IOrmEntity entity = mock(IOrmEntity.class);
        returnItems(entity);
        lenient().when(entity.orm_propValueByName("status")).thenReturn(0);
        // 条件UPDATE命中：本消费者成功抢占
        when(session.updateByExample(any(), any())).thenReturn(1L);

        List<Object> c = new ArrayList<>();
        int n = source.drainTo(c, 1, 0, 0);

        assertEquals(1, n);
        assertSame(entity, c.get(0));
        // 占用字段应被写入
        verify(entity).orm_propValueByName("status", 1);
    }
}
