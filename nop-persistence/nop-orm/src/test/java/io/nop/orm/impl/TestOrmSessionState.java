/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.impl;

import io.nop.app.SimsCollege;
import io.nop.core.lang.sql.SQL;
import io.nop.dao.exceptions.UnknownEntityException;
import io.nop.dao.seq.UuidSequenceGenerator;
import io.nop.orm.AbstractOrmTestCase;
import io.nop.orm.IOrmBatchLoadQueue;
import io.nop.orm.IOrmEntity;
import io.nop.orm.IOrmSession;
import io.nop.orm.OrmEntityState;
import io.nop.orm.id.OrmEntityIdGenerator;
import io.nop.orm.model.IEntityModel;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestOrmSessionState extends AbstractOrmTestCase {

    /**
     * 租户列作为联合主键一部分的实体，保存时主键的租户分量必须被初始化为当前租户
     */
    @Test
    public void testTenantPkInitToCurrentTenant() {
        orm().runInSession(() -> {
            IOrmEntity entity = orm().newEntity("test.entity.TestTenantPkTable");
            orm().save(entity);
            assertTrue(entity.orm_hasId(), "composite pk must be fully initialized");
            assertEquals("123", entity.orm_tenantId());
            orm().flushSession();
        });

        assertEquals(1L, countRows());
    }

    /**
     * 直接验证id生成器：租户主键分量在generateId阶段就必须初始化，而不是依赖session缓存或
     * queueSave阶段processTenantId的兜底补救
     */
    @Test
    public void testGenerateIdInitializesTenantPk() {
        orm().runInSession(session -> {
            IOrmEntity entity = session.newEntity("test.entity.TestTenantPkTable");
            IEntityModel model = sessionFactory.getOrmModel().requireEntityModel("test.entity.TestTenantPkTable");
            new OrmEntityIdGenerator(model, new UuidSequenceGenerator()).generateId(entity);

            assertEquals("123", entity.orm_tenantId());
            assertTrue(entity.orm_hasId());
            return null;
        });
    }

    private long countRows() {
        Object v = jdbc().findFirst(new SQL("select count(*) as CNT from TEST_TENANT_PK_TABLE"));
        return ((Number) v).longValue();
    }

    /**
     * stateless session没有一级缓存的makeTenantId兜底，租户主键分量必须由id生成器在save时初始化。
     * 修复前：save返回后租户分量仍为null（orm_hasId为false），直到flush阶段processTenantId才补救
     */
    @Test
    public void testTenantPkStatelessSave() {
        IOrmSession session = sessionFactory.openSession(true);
        try {
            IOrmEntity entity = session.newEntity("test.entity.TestTenantPkTable");
            session.save(entity);
            // 修复前：复合主键的租户分量为null，orm_hasId()为false
            assertTrue(entity.orm_hasId(), "composite pk must be complete right after save");
            assertEquals("123", entity.orm_tenantId());
            session.flush();
        } finally {
            session.close();
        }

        assertEquals(1L, countRows());
        Object tenantId = jdbc().findFirst(new SQL("select NOP_TENANT_ID as V from TEST_TENANT_PK_TABLE"));
        assertEquals("123", tenantId);
    }

    /**
     * proxy加载后发现记录不存在，orm_requireEntity必须按接口契约抛出异常而不是返回未装配实体
     */
    @Test
    public void testRequireEntityThrowsWhenMissing() {
        orm().runInSession(() -> {
            IOrmEntity entity = orm().load(SimsCollege.class.getName(), "999");
            assertThrows(UnknownEntityException.class, entity::orm_requireEntity);
        });
    }

    /**
     * 删除proxy实体时加载后发现记录不存在，不应标记为DELETING（否则产生多余DELETE和回调）
     */
    @Test
    public void testDeleteMissingProxyIsNoOp() {
        orm().runInSession(() -> {
            IOrmEntity entity = orm().load(SimsCollege.class.getName(), "999");
            orm().delete(entity);
            assertSame(OrmEntityState.MISSING, entity.orm_state());
        });
    }

    /**
     * 批量加载队列的isEmpty()在无待加载对象时返回true，有待加载对象时返回false
     */
    @Test
    public void testBatchLoadQueueIsEmpty() {
        orm().runInSession(session -> {
            IOrmBatchLoadQueue queue = session.getBatchLoadQueue();
            assertTrue(queue.isEmpty(), "fresh queue must be empty");

            IOrmEntity entity = session.load(SimsCollege.class.getName(), "1");
            queue.enqueue(entity);
            assertFalse(queue.isEmpty(), "queue with pending load must not be empty");
            return null;
        });
    }
}
