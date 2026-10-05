/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.shard;

import io.nop.api.core.config.AppConfig;
import io.nop.core.lang.sql.SQL;
import io.nop.dao.DaoConfigs;
import io.nop.dao.dialect.IDialect;
import io.nop.dao.shard.ConfigShardSelector;
import io.nop.dao.shard.IShardSelector;
import io.nop.orm.AbstractOrmTestCase;
import io.nop.orm.ddl.DdlSqlCreator;
import io.nop.orm.ddl.ShardDdlHelper;
import io.nop.orm.model.IColumnModel;
import io.nop.orm.model.IEntityModel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * plan 2320 Phase 3：DDL N表展开（DataBaseSchemaInitializer调用点形态的focused测试）。
 *
 * <p>覆盖：分片表名展开全集、模型视图重定向（table.tableName / col.ownerEntityModel.tableName）、
 * 按展开名建N张物理表、未绑定策略实体建表行为不变、DdlSqlCreator公共语义不变
 * （同一creator既可建基表也可建视图表）。</p>
 */
public class TestShardDdlExpansion extends AbstractOrmTestCase {

    static final String SHARD_ENTITY = "test.entity.TestOrmShardTable";

    private IShardSelector selector;

    @Override
    protected IShardSelector shardSelector() {
        if (selector == null) {
            selector = newSelector("{\"strategies\":{\"test-mod\":{\"type\":\"mod\",\"shard-count\":2,"
                    + "\"entities\":[\"" + SHARD_ENTITY + "\",\"" + TestOrmShardRouting.MAIN_ENTITY + "\"]}}}");
        }
        return selector;
    }

    @AfterEach
    public void tearDownShard() {
        AppConfig.getConfigProvider().updateConfigValue(DaoConfigs.CFG_ORM_SHARD, null);
        selector = null;
    }

    private IShardSelector newSelector(String configJson) {
        ConfigShardSelector selector = new ConfigShardSelector();
        AppConfig.getConfigProvider().updateConfigValue(DaoConfigs.CFG_ORM_SHARD, configJson);
        try {
            selector.init();
        } finally {
            AppConfig.getConfigProvider().updateConfigValue(DaoConfigs.CFG_ORM_SHARD, null);
        }
        return selector;
    }

    private IEntityModel entityModel(String entityName) {
        return sessionFactory.getOrmModel().requireEntityModel(entityName);
    }

    @Test
    public void testShardTableNamesExpansion() {
        IEntityModel model = entityModel(SHARD_ENTITY);
        List<String> names = ShardDdlHelper.shardTableNames(selector, model);
        assertEquals(List.of("TEST_ORM_SHARD_TABLE_0", "TEST_ORM_SHARD_TABLE_1"), names);
        assertTrue(ShardDdlHelper.isShardExpanded(selector, model));

        // 未绑定策略的实体不展开
        IEntityModel plain = entityModel("test.entity.TestOrmSubTable");
        assertFalse(ShardDdlHelper.isShardExpanded(selector, plain));
        assertTrue(ShardDdlHelper.shardTableNames(selector, plain).isEmpty());

        // selector为null不展开（未配置分片体系零变更）
        assertTrue(ShardDdlHelper.shardTableNames(null, model).isEmpty());
    }

    @Test
    public void testExplicitTableNameDdlRedirect() {
        IEntityModel model = entityModel(SHARD_ENTITY);
        IDialect dialect = jdbcTemplate.getDialectForQuerySpace(model.getQuerySpace());
        DdlSqlCreator creator = DdlSqlCreator.forDialect(dialect);
        IColumnModel col = (IColumnModel) model.getColumn("strValue", true);

        // 显式物理表名入口：DDL定向到指定表名
        String createSql = creator.createTable(model, false, "T_1");
        assertTrue(createSql.contains("CREATE TABLE T_1"), createSql);

        String addColumnSql = creator.addColumn(col, "T_1");
        assertTrue(addColumnSql.contains("alter table T_1"), addColumnSql);

        // 不传物理表名时输出与既有方法逐字节一致（公共语义不变）
        assertEquals(creator.createTable(model, false), creator.createTable(model, false, null));
        assertEquals(creator.addColumn(col), creator.addColumn(col, null));
    }

    @Test
    public void testCreateShardTablesByExpansion() {
        IEntityModel model = entityModel(SHARD_ENTITY);
        IDialect dialect = jdbcTemplate.getDialectForQuerySpace(model.getQuerySpace());
        DdlSqlCreator creator = DdlSqlCreator.forDialect(dialect);

        // DataBaseSchemaInitializer调用点的展开逻辑：按分片名全集逐表existsTable+createTable
        for (String physicalTable : ShardDdlHelper.shardTableNames(selector, model)) {
            if (!jdbcTemplate.existsTable(model.getQuerySpace(), physicalTable)) {
                String ddl = creator.createTable(model, false, physicalTable);
                jdbcTemplate.executeUpdate(new SQL(ddl));
            }
        }

        assertTrue(jdbcTemplate.existsTable(null, "TEST_ORM_SHARD_TABLE_0"));
        assertTrue(jdbcTemplate.existsTable(null, "TEST_ORM_SHARD_TABLE_1"));
        // 测试基座(AbstractOrmTestCase.createTables)已建全部基表，此处只断言分片表正确建出；
        // 「逻辑基表不被建出」不变式由 TestShardAutoUpgrade（旧库无基表）覆盖

        // N表可正常写入与查询
        jdbcTemplate.executeUpdate(new SQL("insert into TEST_ORM_SHARD_TABLE_1(sid, STR_VALUE) values('a','x')"));
        List<Map<String, Object>> rows = jdbcTemplate.findAll(new SQL("select * from TEST_ORM_SHARD_TABLE_1"));
        assertEquals(1, rows.size());
    }

    @Test
    public void testAddColumnToShardTableViaExplicitName() {
        IEntityModel model = entityModel(SHARD_ENTITY);
        IDialect dialect = jdbcTemplate.getDialectForQuerySpace(model.getQuerySpace());
        DdlSqlCreator creator = DdlSqlCreator.forDialect(dialect);

        for (String physicalTable : ShardDdlHelper.shardTableNames(selector, model)) {
            String ddl = creator.createTable(model, false, physicalTable);
            jdbcTemplate.executeUpdate(new SQL(ddl));
        }

        // auto-upgrade加列形态：显式物理表名入口把加列DDL定向到分片物理表
        IColumnModel col = (IColumnModel) model.getColumn("strValue", true);
        for (String physicalTable : ShardDdlHelper.shardTableNames(selector, model)) {
            String addColumnSql = creator.addColumn(col, physicalTable);
            // DDL引用的是分片物理表而非基表
            assertTrue(addColumnSql.contains("alter table " + physicalTable), addColumnSql);
        }
    }
}
