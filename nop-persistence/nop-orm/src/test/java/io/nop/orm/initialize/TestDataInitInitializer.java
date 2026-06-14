package io.nop.orm.initialize;

import io.nop.api.core.exceptions.NopException;
import io.nop.dao.api.IEntityDao;
import io.nop.orm.AbstractOrmTestCase;
import io.nop.orm.IOrmEntity;
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
}
