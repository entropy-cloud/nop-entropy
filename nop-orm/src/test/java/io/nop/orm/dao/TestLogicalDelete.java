/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.orm.dao;

import io.nop.app.SimsMajor;
import io.nop.core.lang.sql.SQL;
import io.nop.dao.api.IEntityDao;
import io.nop.orm.AbstractOrmTestCase;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestLogicalDelete extends AbstractOrmTestCase {
    @Test
    public void testLogicalDelete() {
        IEntityDao<SimsMajor> dao = daoProvider().daoFor(SimsMajor.class);
        AtomicReference<String> idRef = new AtomicReference<>();
        orm().runInSession(() -> {
            SimsMajor entity = new SimsMajor();
            entity.setMajorName("ss");
            dao.saveEntity(entity);

            idRef.set(entity.getMajorId());
            dao.deleteEntity(entity);
        });

        // 如果是在保存到数据库之前删除，则实际上不会保存到数据库中
        SimsMajor entity = dao.getEntityById(idRef.get());
        assertNull(entity);

        entity = new SimsMajor();
        entity.setMajorName("sss");
        dao.saveEntity(entity);

        assertEquals((byte) 0, entity.getDelFlag());

        String id = entity.getMajorId();
        orm().runInSession(() -> {
            SimsMajor major = dao.getEntityById(id);
            dao.deleteEntity(major);
        });

        entity = dao.getEntityById(id);
        assertEquals((byte) 1, entity.getDelFlag());

        // 按example查询时会自动过滤已经被标记为逻辑删除的记录
        List<SimsMajor> list = dao.findAllByExample(new SimsMajor());
        assertEquals(0, list.size());

        orm().runInSession(() -> {
            // 禁用逻辑删除后可以物理删除
            SimsMajor major = dao.getEntityById(id);
            major.orm_disableLogicalDelete(true);
            dao.deleteEntity(major);
        });

        assertNull(dao.getEntityById(id));
    }

    @Test
    public void testSelect() {
        SQL sql = SQL.begin().sql("select o from io.nop.app.SimsMajor o").end();

        IEntityDao<SimsMajor> dao = daoProvider().daoFor(SimsMajor.class);
        AtomicReference<String> idRef = new AtomicReference<>();
        orm().runInSession(() -> {
            SimsMajor entity = new SimsMajor();
            entity.setMajorName("ss");
            dao.saveEntity(entity);

            idRef.set(entity.getMajorId());
        });

        orm().runInSession(() -> {
            SimsMajor entity = dao.getEntityById(idRef.get());
            dao.deleteEntity(entity);
        });

        List<SimsMajor> list = orm().findAll(sql);
        assertTrue(list.isEmpty());

        SimsMajor entity = dao.getEntityById(idRef.get());
        assertTrue(entity != null);
        assertTrue(1 == entity.getDelFlag());

        SQL sql1 = SQL.begin().sql("select o from io.nop.app.SimsMajor o").disableLogicalDelete().end();
        list = orm().findAll(sql1);
        assertEquals(1, list.size());
    }
}
