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

import java.util.Collection;

@NopTestConfig(localDb = true, initDatabaseSchema = OptionalBoolean.TRUE)
public abstract class AbstractNopDatavTest extends JunitBaseTestCase {

    @Inject
    IJdbcTemplate jdbcTemplate;

    @Inject
    IOrmSessionFactory ormSessionFactory;

    @BeforeEach
    public void initDatavSchema() {
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
