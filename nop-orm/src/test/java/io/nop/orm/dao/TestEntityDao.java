/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.orm.dao;

import io.nop.api.core.annotations.core.Description;
import io.nop.api.core.annotations.txn.TransactionPropagation;
import io.nop.api.core.beans.query.OrderFieldBean;
import io.nop.api.core.exceptions.NopException;
import io.nop.app.SimsClass;
import io.nop.app.SimsCollege;
import io.nop.app.SimsMajor;
import io.nop.dao.api.IEntityDao;
import io.nop.orm.AbstractOrmTestCase;
import io.nop.orm.IOrmEntity;
import io.nop.orm.IOrmEntitySet;
import io.nop.orm.OrmEntityState;
import io.nop.orm.OrmErrors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestEntityDao extends AbstractOrmTestCase {

    @Test
    public void testSaveEntity() {
        IEntityDao<SimsCollege> dao = daoProvider().daoFor(SimsCollege.class);
        SimsCollege entity = dao.newEntity();
        entity.setCollegeId("3");
        entity.setCollegeName("CollegeC");
        entity.setIntro("intro");
        entity.setShortName("C");
        dao.saveEntity(entity);

        dao.clearEntitySessionCache();

        assertNull(dao.getEntityById("2"));

        assertEquals("C", dao.getEntityById(3).getShortName());
    }

    /**
     * load仅创建proxy对象，get会强制加载
     */
    @Test
    public void testLoad() {
        orm().runInSession(() -> {
            IEntityDao<SimsCollege> dao = daoProvider().daoFor(SimsCollege.class);
            SimsCollege entity = dao.loadEntityById(1);
            assertTrue(entity.orm_proxy());
            assertTrue(!entity.orm_dirty());
            assertTrue(!entity.orm_fullyLoaded());
            assertTrue(!entity.orm_readonly());
            assertTrue(entity.orm_attached());
            assertTrue(!entity.orm_extDirty());
            assertEquals("1", entity.orm_idString());
            assertTrue(entity.orm_proxy());

            dao.getEntityById(1);
            assertTrue(!entity.orm_proxy());
            assertTrue(entity.orm_fullyLoaded());
            assertEquals("A", entity.getShortName());
        });

        insertClass(101, 102);
        SimsClass class1 = (SimsClass) orm().get(SimsClass.class.getName(), 101);
        assertTrue(!class1.orm_proxy());
        assertTrue(!class1.orm_propLoaded(SimsClass.PROP_ID_collegeId));
    }

    /**
     * 如果session已经关闭，则访问proxy对象上的方法将导致报错
     */
    @Test
    public void testDetached() {
        IEntityDao<SimsCollege> dao = daoProvider().daoFor(SimsCollege.class);

        orm().runInSession(() -> {
            SimsCollege entity = dao.loadEntityById(1);
            assertEquals("A", entity.getShortName());
        });

        SimsCollege entity = dao.loadEntityById(1);
        try {
            entity.getShortName();
            assertTrue(false);
        } catch (NopException e) {
            assertEquals(OrmErrors.ERR_ORM_SESSION_CLOSED.getErrorCode(), e.getErrorCode());
        }
    }

    @Test
    public void testDelete() {
        assertNotNull(orm().get(SimsCollege.class.getName(), 1));
        assertNotNull(orm().get(SimsClass.class.getName(), 11));

        orm().runInSession(() -> {
            IEntityDao<SimsCollege> dao = daoProvider().daoFor(SimsCollege.class);
            SimsCollege entity = dao.newEntity();
            entity.setCollegeId("3");
            entity.setCollegeName("CollegeC");
            entity.setIntro("intro");
            entity.setShortName("C");
            dao.saveEntity(entity);

            dao.deleteEntity(entity);
            entity = dao.getEntityById(1);
            dao.deleteEntity(entity);

            SimsClass classEntity = (SimsClass) orm().load(SimsClass.class.getName(), 11);
            orm().delete(classEntity);
        });
        assertNull(orm().get(SimsCollege.class.getName(), 1));
        assertNull(orm().get(SimsCollege.class.getName(), 3));
        assertNull(orm().get(SimsClass.class.getName(), 11));
    }

    @Test
    public void testUpdate() {
        orm().runInSession(() -> {
            IEntityDao<SimsCollege> dao = daoProvider().daoFor(SimsCollege.class);
            SimsCollege entity = dao.getEntityById(1);
            entity.setShortName("B");
            entity.setIntro("ss");
        });

        SimsCollege entity = daoProvider().daoFor(SimsCollege.class).getEntityById(1);
        assertEquals("B", entity.getShortName());
        assertEquals("ss", entity.getIntro());
    }

    @Test
    public void testBatchUpdate() {
        orm().runInSession(() -> {
            // 插入id=3的记录
            IEntityDao<SimsCollege> dao = daoProvider().daoFor(SimsCollege.class);
            SimsCollege entity = dao.newEntity();
            entity.setCollegeId("3");
            entity.setCollegeName("CollegeC");
            entity.setIntro("intro");
            entity.setShortName("C");
            dao.saveEntity(entity);
        });

        orm().runInSession(() -> {
            // 批量更新两条记录
            IEntityDao<SimsCollege> dao = daoProvider().daoFor(SimsCollege.class);
            SimsCollege entity = dao.getEntityById(1);
            entity.setShortName("B");
            entity.setIntro("ss");

            entity = dao.getEntityById(3);
            entity.setShortName("CCC");
            entity.setIntro("pp");
            orm().flushSession();

            // 更新后乐观锁版本号+1
            assertEquals(1, entity.getRevision());
        });

        SimsCollege entity = daoProvider().daoFor(SimsCollege.class).getEntityById(1);
        assertEquals("B", entity.getShortName());
        assertEquals("ss", entity.getIntro());

        entity = daoProvider().daoFor(SimsCollege.class).getEntityById(3);
        assertEquals("CCC", entity.getShortName());
        assertEquals("pp", entity.getIntro());
        assertEquals(1, entity.getRevision());
    }

    @Test
    public void testBatchSave() {
        List<Integer> ids = insertColleges(100, 2000);

        Map<Object, SimsCollege> entities = daoProvider().daoFor(SimsCollege.class).batchGetEntityMapByIds(ids);
        assertEquals(2000 - 100 + 1, entities.size());

        orm().runInSession(() -> {
            List<SimsCollege> list = daoProvider().daoFor(SimsCollege.class).batchGetEntitiesByIds(ids);
            daoProvider().daoFor(SimsCollege.class).batchDeleteEntities(list);
        });

        List<SimsCollege> list = daoProvider().daoFor(SimsCollege.class).batchGetEntitiesByIds(ids);
        assertEquals(2000 - 100 + 1, entities.size());

        for (SimsCollege college : list) {
            assertEquals(OrmEntityState.MISSING, college.orm_state());
        }
    }

    @Test
    public void testByExample() {
        insertColleges(100, 105);
        SimsCollege example = new SimsCollege();
        example.setIntro("intro101");

        IEntityDao<SimsCollege> dao = daoProvider().daoFor(SimsCollege.class);
        IOrmEntity entity = dao.findFirstByExample(example);
        assertEquals("101", entity.orm_id());

        assertEquals(1,
                dao.findAllByExample(example, Arrays.asList(OrderFieldBean.forField(SimsCollege.PROP_NAME_createdBy)))
                        .size());

        assertEquals(1, dao.countByExample(example));

        dao.deleteByExample(example);
        assertNull(dao.findFirstByExample(example));
    }

    @Test
    public void testCollection() {
        orm().runInSession(() -> {
            IEntityDao<SimsClass> dao = daoProvider().daoFor(SimsClass.class);
            SimsClass entity = dao.getEntityById(11);
            assertTrue(entity.getSimsCollege().orm_state().isProxy());
            IOrmEntitySet classes = (IOrmEntitySet) (Set) entity.getSimsCollege().getSimsClasses();
            assertEquals(classes, entity.getSimsCollege().orm_refEntitySet(SimsCollege.PROP_NAME_simsClasses));
            assertTrue(classes.orm_proxy());

            assertEquals(1, classes.size());
            assertEquals(entity, classes.iterator().next());
        });

    }

    @Test
    public void testBatchLoad() {
        insertColleges(100, 105);
        insertClass(100, 105);

        orm().runInSession(() -> {
            IEntityDao<SimsClass> dao = daoProvider().daoFor(SimsClass.class);
            SimsClass class1 = dao.loadEntityById(101);
            SimsClass class2 = dao.loadEntityById(102);

            dao.batchGetEntities(Arrays.asList(class1, class2));
            assertTrue(!class1.orm_proxy());
            assertTrue(!class2.orm_proxy());

            // college属性是延迟加载的
            assertTrue(!class1.orm_propLoaded(SimsClass.PROP_ID_collegeId));

            assertTrue(class1.getSimsCollege().orm_proxy());
            dao.batchLoadProps(Arrays.asList(class1, class2), Arrays.asList(SimsClass.PROP_NAME_simsCollege));
            assertTrue(!class1.getSimsCollege().orm_proxy());
        });
    }

    @Test
    public void testBatchLoadCollection() {
        insertColleges(100, 105);
        insertClass(100, 105);

        orm().runInSession(() -> {
            IEntityDao<SimsCollege> dao = daoProvider().daoFor(SimsCollege.class);
            SimsCollege college1 = dao.loadEntityById(101);
            SimsCollege college2 = dao.loadEntityById(102);

            dao.batchLoadProps(Arrays.asList(college1, college2), Arrays.asList(SimsCollege.PROP_NAME_simsClasses));
            assertTrue(!college1.orm_refEntitySet(SimsCollege.PROP_NAME_simsClasses).orm_proxy());
            assertEquals(1, college1.getSimsClasses().size());
        });
    }

    @Test
    public void testFindAll() {
        List<SimsClass> list = (List) daoProvider().dao(SimsClass.class.getName()).findAll();
        assertEquals(1, list.size());
    }

    @Test
    public void testLock() {
        AtomicReference<SimsClass> ref = new AtomicReference<>();

        orm().runInSession(() -> {
            txn().runInTransaction(null, TransactionPropagation.REQUIRED, txn -> {
                IEntityDao<SimsClass> dao = daoProvider().daoFor(SimsClass.class);
                SimsClass entity = dao.loadEntityById(11);
                dao.lockEntity(entity);
                assertTrue(entity.orm_locked());
                ref.set(entity);
                return null;
            });
        });

        assertTrue(!ref.get().orm_locked());
        assertTrue(!ref.get().orm_attached());
    }

    @DisplayName("自动生成主键")
    @Test
    public void testSeqGenerator() {
        IEntityDao<SimsMajor> dao = daoProvider().daoFor(SimsMajor.class);
        SimsMajor entity = new SimsMajor();
        entity.setMajorName("ss");
        dao.saveEntity(entity);

        assertTrue(entity.getMajorId() != null);
    }

    @Description("为了兼容不同的数据库，空字符串总是保存为null")
    @Test
    public void testEmptyStringAsNull() {
        IEntityDao<SimsCollege> dao = daoProvider().daoFor(SimsCollege.class);
        SimsCollege entity = dao.newEntity();
        entity.setCollegeId("333");
        entity.setCollegeName("CollegeCCC");
        entity.setIntro("");
        entity.setShortName("C");
        dao.saveEntity(entity);

        dao.clearEntitySessionCache();

        SimsCollege loaded = dao.getEntityById(333);
        assertNull(loaded.getIntro());
        assertEquals("CollegeCCC", loaded.getCollegeName());
    }

}
