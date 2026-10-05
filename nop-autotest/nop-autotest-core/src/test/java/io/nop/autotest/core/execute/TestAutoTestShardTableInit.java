/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.autotest.core.execute;

import com.zaxxer.hikari.HikariDataSource;
import io.nop.api.core.config.AppConfig;
import io.nop.commons.metrics.GlobalMeterRegistry;
import io.nop.commons.util.StringHelper;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.lang.sql.SQL;
import io.nop.core.resource.IResource;
import io.nop.core.resource.VirtualFileSystem;
import io.nop.dao.DaoConfigs;
import io.nop.dao.dialect.IDialect;
import io.nop.dao.jdbc.IJdbcTemplate;
import io.nop.dao.jdbc.impl.JdbcFactory;
import io.nop.dao.seq.UuidSequenceGenerator;
import io.nop.dao.shard.ConfigShardSelector;
import io.nop.dao.shard.IShardSelector;
import io.nop.dao.txn.ITransactionTemplate;
import io.nop.orm.ddl.DdlSqlCreator;
import io.nop.orm.ddl.ShardDdlHelper;
import io.nop.orm.factory.DefaultOrmColumnBinderEnhancer;
import io.nop.orm.factory.OrmSessionFactoryBean;
import io.nop.orm.model.IEntityModel;
import io.nop.orm.model.OrmModel;
import io.nop.orm.model.loader.OrmModelLoader;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import java.util.Collections;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * plan 2320 Phase 3：AutoTestCaseDataBaseInitializer（第三建表调用点）的N表展开focused测试。
 * 直接以包内可见的createTable入口驱动，验证绑定策略的useShard实体按分片名全集建N张物理表、
 * 未绑定策略实体维持单表建表行为。
 */
public class TestAutoTestShardTableInit {

    private static HikariDataSource dataSource;
    private IJdbcTemplate jdbc;
    private AutoTestCaseDataBaseInitializer initializer;
    private IShardSelector selector;

    @BeforeAll
    public static void init() {
        // 本模块测试环境的IoC装配要求datasource配置存在（本测试实际不使用该数据源，
        // 仅需通过初始化）；给出H2占位配置
        System.setProperty("nop.datasource.driver-class-name", "org.h2.Driver");
        System.setProperty("nop.datasource.jdbc-url",
                "jdbc:h2:mem:" + StringHelper.generateUUID() + ";DB_CLOSE_DELAY=-1");
        System.setProperty("nop.datasource.username", "sa");
        System.setProperty("nop.datasource.password", "");
        CoreInitialization.initialize();

        dataSource = new HikariDataSource();
        dataSource.setJdbcUrl("jdbc:h2:mem:" + StringHelper.generateUUID() + ";DB_CLOSE_DELAY=-1");
        dataSource.setDriverClassName("org.h2.Driver");
        dataSource.setUsername("sa");
        dataSource.setPassword("");
        dataSource.setMaximumPoolSize(2);
        dataSource.setMetricRegistry(GlobalMeterRegistry.instance());
    }

    @AfterAll
    public static void destroy() {
        if (dataSource != null)
            dataSource.close();
        CoreInitialization.destroy();
    }

    @BeforeEach
    public void setUp() {
        JdbcFactory factory = new JdbcFactory();
        ITransactionTemplate txn = factory.newTransactionTemplate(dataSource);
        jdbc = factory.newJdbcTemplate(txn);

        selector = newShardSelector("{\"strategies\":{\"autotest-mod\":{\"type\":\"mod\",\"shard-count\":2,"
                + "\"entities\":[\"test.shardupg.ShardUpgEntity\"]}}}");

        // caseData/daoProvider为null：本测试只驱动createTable入口，不触及其余初始化路径
        initializer = new AutoTestCaseDataBaseInitializer(null, true, false, false, false, null, null, jdbc);
    }

    @AfterEach
    public void tearDown() {
        AppConfig.getConfigProvider().updateConfigValue(DaoConfigs.CFG_ORM_SHARD, null);
    }

    private IShardSelector newShardSelector(String configJson) {
        ConfigShardSelector selector = new ConfigShardSelector();
        AppConfig.getConfigProvider().updateConfigValue(DaoConfigs.CFG_ORM_SHARD, configJson);
        try {
            selector.init();
        } finally {
            AppConfig.getConfigProvider().updateConfigValue(DaoConfigs.CFG_ORM_SHARD, null);
        }
        return selector;
    }

    private void cleanTables() {
        // 两个测试共用同一个H2库（类级静态数据源），先清理避免相互影响
        for (String table : new String[]{"T_SHARD_UPG", "T_SHARD_UPG_0", "T_SHARD_UPG_1"}) {
            jdbc.executeUpdate(new SQL("drop table if exists " + table));
        }
    }

    private IEntityModel loadShardEntityModel() {
        IResource resource = VirtualFileSystem.instance().getResource("/nop/dbtool/test/shard-upgrade.orm.xml");
        OrmModel model = new OrmModelLoader().loadOrmModel(Collections.singletonList(resource));
        return model.requireEntityModel("test.shardupg.ShardUpgEntity");
    }

    @Test
    public void testCreateShardTablesForBoundEntity() {
        cleanTables();
        IEntityModel entityModel = loadShardEntityModel();
        IDialect dialect = jdbc.getDialectForQuerySpace(null);

        initializer.createTable(dialect, entityModel, selector);

        assertTrue(jdbc.existsTable(null, "T_SHARD_UPG_0"), "shard table 0 must be created");
        assertTrue(jdbc.existsTable(null, "T_SHARD_UPG_1"), "shard table 1 must be created");
        assertFalse(jdbc.existsTable(null, "T_SHARD_UPG"), "logical base table must not be created");
    }

    @Test
    public void testCreateSingleTableForUnboundEntity() {
        cleanTables();
        IEntityModel entityModel = loadShardEntityModel();
        IDialect dialect = jdbc.getDialectForQuerySpace(null);
        DdlSqlCreator creator = DdlSqlCreator.forDialect(dialect.getName());

        // 未绑定策略的实体（shard-upgrade模型中无其他实体，此处用另一模型的实体语义验证：
        // 未展开时走单表建表路径）
        IShardSelector emptySelector = newShardSelector(
                "{\"strategies\":{\"other\":{\"type\":\"mod\",\"shard-count\":2,"
                        + "\"entities\":[\"no.such.Entity\"]}}}");

        initializer.createTable(dialect, entityModel, emptySelector);

        assertTrue(jdbc.existsTable(null, "T_SHARD_UPG"), "base table must be created for unbound entity");
        assertFalse(jdbc.existsTable(null, "T_SHARD_UPG_0"));
        assertFalse(jdbc.existsTable(null, "T_SHARD_UPG_1"));

        // 生成DDL经显式表名入口传入同一表名时输出与既有方法一致（公共语义不变）
        String directSql = creator.createTable(entityModel, true);
        org.junit.jupiter.api.Assertions.assertEquals(directSql,
                creator.createTable(entityModel, true, entityModel.getTableName()));
    }
}
