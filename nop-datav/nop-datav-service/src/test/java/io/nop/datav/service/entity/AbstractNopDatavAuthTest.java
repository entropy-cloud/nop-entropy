package io.nop.datav.service.entity;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.annotations.core.OptionalBoolean;
import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.core.lang.sql.SQL;
import io.nop.dao.dialect.IDialect;
import io.nop.dao.jdbc.IJdbcTemplate;
import io.nop.orm.IOrmSessionFactory;
import io.nop.orm.ddl.DdlSqlCreator;
import io.nop.orm.model.IEntityModel;
import io.nop.orm.model.IOrmModel;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInfo;

import java.util.Collection;

/**
 * Base class for nop-datav auth integration tests.
 * <p>
 * Uses testConfigFile to point site-map and data-auth to the test-specific XML files
 * under _vfs/test/datav/auth/. Each subclass enables the auth switches it needs via
 * its own @NopTestConfig.
 * <p>
 * Overrides {@link #init(TestInfo)} to create ALL entity tables (including nop-auth/nop-sys
 * tables like NOP_SYS_SEQUENCE) BEFORE lazy actions run, because nop-sys-dao's
 * SysSequenceGenerator.lazyInit() (a deferred lazy action) queries NOP_SYS_SEQUENCE
 * during {@link #runLazyActions()}.
 */
@NopTestConfig(localDb = true, initDatabaseSchema = OptionalBoolean.TRUE,
        testConfigFile = "classpath:nop-datav-auth-test.yaml")
public abstract class AbstractNopDatavAuthTest extends JunitBaseTestCase {

    @Inject
    IJdbcTemplate jdbcTemplate;

    @Inject
    IOrmSessionFactory ormSessionFactory;

    public AbstractNopDatavAuthTest() {
        setTestConfig("nop.orm.init-database-schema", true);
    }

    @Override
    @BeforeEach
    public void init(TestInfo testInfo) {
        clearLazyActions();
        initBeans();

        // After initBeans(), the IoC container is started and beans are injected.
        // Create ALL entity tables BEFORE runLazyActions() to ensure system tables
        // (e.g. NOP_SYS_SEQUENCE queried by SysSequenceGenerator.lazyInit()) exist.
        createAllTables();

        runLazyActions();
    }

    private void createAllTables() {
        IOrmModel ormModel = ormSessionFactory.getOrmModel();
        Collection<? extends IEntityModel> tables = ormModel.getEntityModelsInTopoOrder();
        for (IEntityModel table : tables) {
            if (table.isTableView())
                continue;
            String querySpace = table.getQuerySpace();
            IDialect dialect = jdbcTemplate.getDialectForQuerySpace(querySpace);
            String createSql = new DdlSqlCreator(dialect).createTable(table, false);
            if (!jdbcTemplate.existsTable(querySpace, table.getTableName())) {
                jdbcTemplate.executeUpdate(SQL.begin().querySpace(querySpace)
                        .name("create:" + table.getTableName()).sql(createSql).end());
            }
        }
    }
}
