/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.dbtool.core;

import com.zaxxer.hikari.HikariDataSource;
import io.nop.api.core.config.AppConfig;
import io.nop.api.core.ioc.IBeanProvider;
import io.nop.commons.metrics.GlobalMeterRegistry;
import io.nop.commons.util.StringHelper;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.lang.sql.SQL;
import io.nop.core.resource.IResource;
import io.nop.core.resource.VirtualFileSystem;
import io.nop.dao.DaoConfigs;
import io.nop.dao.jdbc.IJdbcTemplate;
import io.nop.dao.jdbc.impl.JdbcFactory;
import io.nop.dao.seq.UuidSequenceGenerator;
import io.nop.dao.shard.ConfigShardSelector;
import io.nop.dao.shard.IShardSelector;
import io.nop.dao.txn.ITransactionTemplate;
import io.nop.dbtool.core.diff.OrmDbDiffer;
import io.nop.orm.IOrmSessionFactory;
import io.nop.orm.factory.DefaultOrmColumnBinderEnhancer;
import io.nop.orm.factory.IOrmModelProvider;
import io.nop.orm.ILoadedOrmModel;
import io.nop.orm.factory.LoadedOrmModel;
import io.nop.orm.factory.OrmSessionFactoryBean;
import io.nop.orm.model.OrmModel;
import io.nop.orm.model.loader.OrmModelLoader;
import io.nop.orm.persister.IPersistEnv;
import jakarta.annotation.Nonnull;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * plan 2320 Phase 3：auto-upgrade 对 N 张分片物理表逐一列 diff（focused test，H2 真库执行）。
 *
 * <p>旧库形态：仅两张分片物理表 T_SHARD_UPG_0/1（缺模型新增列），逻辑基表不存在。
 * {@link DataBaseUpgrader}（{@link OrmDbDiffer} 差异判定 + shardSelector注入）应：
 * (a) 把发现的分片物理表归一到逻辑实体做diff匹配；(b) 对每张分片表逐一ADD COLUMN；
 * (c) 不加列到不存在的逻辑基表。测试模型经显式资源加载 + 静态IOrmModelProvider注入，
 * 形态参照 {@code TestEncAssistedAutoUpgrade}（plan 2318先例）。</p>
 */
public class TestShardAutoUpgrade {

    private static HikariDataSource dataSource;
    private IJdbcTemplate jdbc;
    private OrmSessionFactoryBean factoryBean;
    private StaticModelProvider modelProvider;

    @BeforeAll
    public static void init() {
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

        OrmModel model = loadModel();
        modelProvider = new StaticModelProvider(model);
        factoryBean = new OrmSessionFactoryBean();
        factoryBean.setJdbcTemplate(jdbc);
        factoryBean.setBeanProvider(EmptyBeanProvider.INSTANCE);
        factoryBean.setSequenceGenerator(new UuidSequenceGenerator());
        factoryBean.setColumnBinderEnhancer(new DefaultOrmColumnBinderEnhancer());
        factoryBean.setOrmModelHolder(modelProvider);
        factoryBean.setShardSelector(newShardSelector());
        factoryBean.init();
    }

    @AfterEach
    public void tearDown() {
        if (factoryBean != null)
            factoryBean.destroy();
        AppConfig.getConfigProvider().updateConfigValue(DaoConfigs.CFG_ORM_SHARD, null);
    }

    private IShardSelector newShardSelector() {
        ConfigShardSelector selector = new ConfigShardSelector();
        AppConfig.getConfigProvider().updateConfigValue(DaoConfigs.CFG_ORM_SHARD,
                "{\"strategies\":{\"upg-mod\":{\"type\":\"mod\",\"shard-count\":2,"
                        + "\"entities\":[\"test.shardupg.ShardUpgEntity\"]}}}");
        try {
            selector.init();
        } finally {
            AppConfig.getConfigProvider().updateConfigValue(DaoConfigs.CFG_ORM_SHARD, null);
        }
        return selector;
    }

    private OrmModel loadModel() {
        IResource resource = VirtualFileSystem.instance().getResource("/nop/dbtool/test/shard-upgrade.orm.xml");
        assertTrue(resource.exists(), "test orm model must exist: " + resource);
        return new OrmModelLoader().loadOrmModel(Collections.singletonList(resource));
    }

    /** 旧库：仅两张分片物理表，缺模型中后加的note列（模拟模型演进） */
    private void createLegacyShardTables() {
        for (String suffix : new String[]{"0", "1"}) {
            jdbc.executeMultiSql(new SQL("drop table if exists T_SHARD_UPG_" + suffix));
            jdbc.executeMultiSql(new SQL(
                    "create table T_SHARD_UPG_" + suffix + " (\n"
                            + "  SID bigint primary key,\n"
                            + "  TENANT_ID varchar(32),\n"
                            + "  EXAM_NAME varchar(100)\n"
                            + ")"));
        }
        jdbc.executeUpdate(new SQL("insert into T_SHARD_UPG_0 (SID, TENANT_ID, EXAM_NAME) values (1, 't0', 'x')"));
    }

    private List<String> columnNames(String table) {
        return jdbc.findAll(new SQL(
                        "select COLUMN_NAME from INFORMATION_SCHEMA.COLUMNS where TABLE_NAME = '" + table
                                + "' order by COLUMN_NAME"),
                (row, rowNumber, colMapper) -> row.getString(0));
    }

    @Test
    public void testAutoUpgradeAddsColumnToEveryShardTable() {
        createLegacyShardTables();

        assertFalse(columnNames("T_SHARD_UPG_0").contains("NOTE"), "legacy shard table must not have NOTE");
        assertFalse(columnNames("T_SHARD_UPG_1").contains("NOTE"), "legacy shard table must not have NOTE");

        IOrmSessionFactory sessionFactory = factoryBean.getObject();
        new DataBaseUpgrader(sessionFactory).upgrade();

        // N张分片表逐一加列
        assertTrue(columnNames("T_SHARD_UPG_0").contains("NOTE"),
                "auto-upgrade must ADD COLUMN to shard table 0: " + columnNames("T_SHARD_UPG_0"));
        assertTrue(columnNames("T_SHARD_UPG_1").contains("NOTE"),
                "auto-upgrade must ADD COLUMN to shard table 1: " + columnNames("T_SHARD_UPG_1"));

        // 不加列到不存在的逻辑基表（逻辑基表也不被建出）
        assertFalse(jdbc.existsTable(null, "T_SHARD_UPG"), "logical base table must not be created");

        // 存量数据不受影响
        Map<String, Object> row = jdbc.findFirst(new SQL("select * from T_SHARD_UPG_0 where SID = 1"));
        assertEquals("x", row.get("EXAM_NAME"));
    }

    /** 空实现：升级验证不触碰 IoC 容器 */
    static class EmptyBeanProvider implements IBeanProvider {
        static final EmptyBeanProvider INSTANCE = new EmptyBeanProvider();

        @Override
        public boolean containsBean(String name) {
            return false;
        }

        @Nonnull
        @Override
        public <T> T getBeanByType(Class<T> clazz) {
            throw new UnsupportedOperationException();
        }

        @Nonnull
        @Override
        public Object getBean(String name) {
            throw new UnsupportedOperationException();
        }

        @Override
        public String getBeanScope(String name) {
            return null;
        }
    }

    /** 显式注入已加载模型（不依赖模块扫描） */
    static class StaticModelProvider implements IOrmModelProvider {
        final OrmModel model;
        private volatile LoadedOrmModel loaded;

        StaticModelProvider(OrmModel model) {
            this.model = model;
        }

        @Override
        public ILoadedOrmModel getOrmModel(IPersistEnv env) {
            if (loaded == null)
                loaded = new LoadedOrmModel(env, model);
            return loaded;
        }

        @Override
        public void clearCache() {
            loaded = null;
        }

        @Override
        public void clearCacheForTenant(String tenantId) {
            loaded = null;
        }

        @Override
        public void close() {
        }
    }
}
