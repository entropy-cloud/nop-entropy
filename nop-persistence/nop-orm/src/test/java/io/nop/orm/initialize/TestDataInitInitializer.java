package io.nop.orm.initialize;

import io.nop.api.core.exceptions.NopException;
import io.nop.dao.api.IEntityDao;
import io.nop.orm.AbstractOrmTestCase;
import io.nop.orm.IOrmEntity;
import io.nop.orm.dao.OrmDaoProvider;
import io.nop.orm.impl.OrmTemplateImpl;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class TestDataInitInitializer extends AbstractOrmTestCase {

    @Override
    protected void prepareData() {
    }

    @Test
    public void testLoadCsvData() {
        DataInitInitializer initializer = new DataInitInitializer();
        initializer.setOrmSessionFactory(sessionFactory);
        initializer.setDaoProvider(daoProvider());
        initializer.setOrmTemplate(orm());
        initializer.setJdbcTemplate(jdbc());
        initializer.setDataLocation("/_test-init-data/");

        initializer.init();

        IEntityDao<IOrmEntity> dao = daoProvider().daoForTable("sims_college");

        List<IOrmEntity> all = dao.findAll();
        assertFalse(all.isEmpty());

        IOrmEntity csvEntity = dao.getEntityById("csv-col-01");
        assertNotNull(csvEntity);
        assertEquals("CSV College A", csvEntity.orm_propValueByName("collegeName"));

        IOrmEntity sqlEntity = dao.getEntityById("sql-col-01");
        assertNotNull(sqlEntity);
        assertEquals("SQL College", sqlEntity.orm_propValueByName("collegeName"));
    }

    @Test
    public void testEmptyCsv() {
        DataInitInitializer initializer = new DataInitInitializer();
        initializer.setOrmSessionFactory(sessionFactory);
        initializer.setDaoProvider(daoProvider());
        initializer.setOrmTemplate(orm());
        initializer.setJdbcTemplate(jdbc());
        initializer.setDataLocation("/_test-init-data-empty/");

        initializer.init();

        IEntityDao<IOrmEntity> dao = daoProvider().daoForTable("sims_college");
        assertTrue(dao.findAll().isEmpty());
    }

    @Test
    public void testColumnMismatch() {
        DataInitInitializer initializer = new DataInitInitializer();
        initializer.setOrmSessionFactory(sessionFactory);
        initializer.setDaoProvider(daoProvider());
        initializer.setOrmTemplate(orm());
        initializer.setJdbcTemplate(jdbc());
        initializer.setDataLocation("/_test-init-data-bad/");

        assertThrows(NopException.class, initializer::init);
    }

    @Test
    public void testSqlFileOnly() {
        DataInitInitializer initializer = new DataInitInitializer();
        initializer.setOrmSessionFactory(sessionFactory);
        initializer.setDaoProvider(daoProvider());
        initializer.setOrmTemplate(orm());
        initializer.setJdbcTemplate(jdbc());
        initializer.setDataLocation("/_test-init-data-sql/");

        initializer.init();

        IEntityDao<IOrmEntity> dao = daoProvider().daoForTable("sims_college");
        assertFalse(dao.isEmpty());
        assertNotNull(dao.getEntityById("sql-only-01"));
    }

    @Test
    public void testInitWhenOrmTemplateSessionFactoryMissing() {
        // 模拟 IoC 创建顺序问题：nopOrmTemplate 已被创建但 sessionFactory 尚未注入
        //（对应 a3fb3f620 修复的 ormTemplate.sessionFactory==null 场景）
        OrmTemplateImpl bareTemplate = new OrmTemplateImpl();
        DataInitInitializer initializer = new DataInitInitializer();
        initializer.setOrmSessionFactory(sessionFactory);
        initializer.setDaoProvider(new OrmDaoProvider(bareTemplate));
        initializer.setOrmTemplate(bareTemplate);
        initializer.setJdbcTemplate(jdbc());
        initializer.setDataLocation("/_test-init-data-npe/");

        assertDoesNotThrow(initializer::init);

        assertEquals(sessionFactory, bareTemplate.getSessionFactory());

        IEntityDao<IOrmEntity> dao = daoProvider().daoForTable("sims_college");
        IOrmEntity entity = dao.getEntityById("npe-col-01");
        assertNotNull(entity);
        assertEquals("Patched Session College", entity.orm_propValueByName("collegeName"));
    }

    @Test
    public void testInitWithoutOrmTemplateThrowsNullPointerException() {
        // 模拟 IoC 按类型注入失败：ormTemplate 为 null 时直接调用 @PostConstruct
        // 当前实现会 NPE（init() 中 ormTemplate.runInSession），属于报告的缺省装配顺序问题的复现
        DataInitInitializer initializer = new DataInitInitializer();
        initializer.setOrmSessionFactory(sessionFactory);
        initializer.setDaoProvider(daoProvider());
        initializer.setJdbcTemplate(jdbc());
        initializer.setDataLocation("/_test-init-data-npe/");

        assertThrows(NullPointerException.class, initializer::init);
    }
}
