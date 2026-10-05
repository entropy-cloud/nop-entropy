/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.dbtool.core;

import com.zaxxer.hikari.HikariDataSource;
import io.nop.api.core.ioc.IBeanProvider;
import io.nop.commons.metrics.GlobalMeterRegistry;
import io.nop.commons.util.StringHelper;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.lang.sql.SQL;
import io.nop.core.resource.IResource;
import io.nop.core.resource.VirtualFileSystem;
import io.nop.dao.jdbc.IJdbcTemplate;
import io.nop.dao.jdbc.impl.JdbcFactory;
import io.nop.dao.seq.UuidSequenceGenerator;
import io.nop.dao.txn.ITransactionTemplate;
import io.nop.dbtool.core.diff.OrmDbDiffer;
import io.nop.orm.IOrmSessionFactory;
import io.nop.orm.factory.DefaultOrmColumnBinderEnhancer;
import io.nop.orm.factory.IOrmModelProvider;
import io.nop.orm.ILoadedOrmModel;
import io.nop.orm.factory.LoadedOrmModel;
import io.nop.orm.factory.OrmSessionFactoryBean;
import io.nop.orm.model.IEntityModel;
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
 * plan 2318 Phase 5：模型已声明辅助列（ext:enc-assisted / ext:enc-like）时，
 * {@link DataBaseUpgrader}（{@link OrmDbDiffer} 差异判定）对存量库自动 ADD COLUMN，
 * 且存量数据不受影响（focused test，H2 真库执行）。
 *
 * <p>旧库形态：仅有主表列（辅助列声明加入前的存量库），并预置一行 @enc: 密文。
 * 测试模型经显式资源加载 + 静态 IOrmModelProvider 注入（不依赖模块注册），
 * 形态参照 {@code TestJdbcMetaDiscovery}（H2 mem + CoreInitialization）。</p>
 */
public class TestEncAssistedAutoUpgrade {

    private static HikariDataSource dataSource;
    private IJdbcTemplate jdbc;
    private OrmSessionFactoryBean factoryBean;

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
        factoryBean = new OrmSessionFactoryBean();
        factoryBean.setJdbcTemplate(jdbc);
        factoryBean.setBeanProvider(EmptyBeanProvider.INSTANCE);
        factoryBean.setSequenceGenerator(new UuidSequenceGenerator());
        factoryBean.setColumnBinderEnhancer(new DefaultOrmColumnBinderEnhancer());
        factoryBean.setOrmModelHolder(new StaticModelProvider(model));
        factoryBean.init();
    }

    @AfterEach
    public void tearDown() {
        if (factoryBean != null)
            factoryBean.destroy();
    }

    private OrmModel loadModel() {
        IResource resource = VirtualFileSystem.instance().getResource("/nop/dbtool/test/enc-upgrade.orm.xml");
        assertTrue(resource.exists(), "test orm model must exist: " + resource);
        return new OrmModelLoader().loadOrmModel(Collections.singletonList(resource));
    }

    /** 旧库：只有主表列（辅助列声明加入前），并预置一行 @enc: 存量密文 */
    private void createLegacyDatabase() {
        jdbc.executeMultiSql(new SQL("drop table if exists T_ENC_UPG"));
        jdbc.executeMultiSql(new SQL(
                "create table T_ENC_UPG (\n"
                        + "  SID bigint primary key,\n"
                        + "  EXAM_NAME varchar(100)\n"
                        + ")"));
        jdbc.executeUpdate(SQL.begin()
                .sql("insert into T_ENC_UPG (SID, EXAM_NAME) values (")
                .param(1L)
                .sql(",")
                .param("@enc:v1:LegacyStoredCiphertextForUpgradeTest")
                .sql(")")
                .end());
    }

    private List<String> columnNames() {
        return jdbc.findAll(new SQL(
                "select COLUMN_NAME from INFORMATION_SCHEMA.COLUMNS where TABLE_NAME = 'T_ENC_UPG' order by COLUMN_NAME"),
                (row, rowNumber, colMapper) -> row.getString(0));
    }

    @Test
    public void testAutoUpgradeAddsHelperColumns() {
        createLegacyDatabase();

        assertFalse(columnNames().contains("EXAM_NAME_ASSISTED"), "legacy db must not have assisted column");
        assertFalse(columnNames().contains("EXAM_NAME_LIKE"), "legacy db must not have like column");

        IOrmSessionFactory sessionFactory = factoryBean.getObject();
        new DataBaseUpgrader(sessionFactory).upgrade();

        List<String> columns = columnNames();
        assertTrue(columns.contains("EXAM_NAME_ASSISTED"),
                "auto-upgrade must ADD COLUMN for enc-assisted declaration: " + columns);
        assertTrue(columns.contains("EXAM_NAME_LIKE"),
                "auto-upgrade must ADD COLUMN for enc-like declaration: " + columns);

        // 存量数据不受升级影响
        Map<String, Object> row = jdbc.findFirst(new SQL("select * from T_ENC_UPG where SID = 1"));
        assertEquals("@enc:v1:LegacyStoredCiphertextForUpgradeTest", row.get("EXAM_NAME"));
    }

    @Test
    public void testUpgradeIsNoopWhenColumnsExist() {
        // 与模型一致的存量库：无差异时不产生 DDL（no-change 路径）
        jdbc.executeMultiSql(new SQL("drop table if exists T_ENC_UPG"));
        jdbc.executeMultiSql(new SQL(
                "create table T_ENC_UPG (\n"
                        + "  SID bigint primary key,\n"
                        + "  EXAM_NAME varchar(100),\n"
                        + "  EXAM_NAME_ASSISTED varchar(64),\n"
                        + "  EXAM_NAME_LIKE varchar(2048)\n"
                        + ")"));

        IOrmSessionFactory sessionFactory = factoryBean.getObject();
        // 不抛异常即通过（no-change 时 DataBaseUpgrader 记 warn 并跳过执行）
        new DataBaseUpgrader(sessionFactory).upgrade();
        assertEquals(0, jdbc.findLong(new SQL("select count(*) from T_ENC_UPG"), 0L));
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
        private final OrmModel model;
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
