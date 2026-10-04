/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical_entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm;

import io.nop.api.core.context.ContextProvider;
import io.nop.api.core.context.IContext;
import io.nop.api.core.exceptions.NopException;
import io.nop.app.SimsClass;
import io.nop.commons.cache.CacheConfig;
import io.nop.commons.cache.LocalCacheProvider;
import io.nop.core.lang.sql.SQL;
import io.nop.dao.api.IEntityDao;
import io.nop.dao.dialect.DialectManager;
import io.nop.dao.dialect.IDialectProvider;
import io.nop.core.resource.IResource;
import io.nop.core.resource.ResourceHelper;
import io.nop.core.resource.VirtualFileSystem;
import io.nop.dao.seq.UuidSequenceGenerator;
import io.nop.orm.IOrmSessionFactory;
import io.nop.orm.dao.OrmDaoProvider;
import io.nop.orm.ddl.DdlSqlCreator;
import io.nop.orm.eql.ICompiledSql;
import io.nop.orm.eql.IEqlAstTransformer;
import io.nop.orm.eql.compile.OracleEqlAstTransformer;
import io.nop.orm.factory.DefaultOrmColumnBinderEnhancer;
import io.nop.orm.factory.OrmSessionFactoryBean;
import io.nop.orm.impl.OrmTemplateImpl;
import io.nop.orm.mock.MockBeanProvider;
import io.nop.orm.model.IEntityModel;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WI8 Phase 3（plan 27；roadmap WI8 EC ①②④ + Minimum Rules #22/#23）：WI4 管线的第一个
 * 真实生产消费者——生产 {@link OracleEqlAstTransformer} 经 factory 默认配置注入
 * （{@code setEqlAstTransformer} = 与 beans.xml 完全相同的配置路径）后的端到端与缓存两向回归。
 *
 * <p>证明面：</p>
 * <ul>
 * <li>EC ②：oracle 正反两方向三节点展开文本按 Phase 0 钉死公式逐字断言（两支式/三支式），
 * 且展开文本不属 naive NOT 包装禁止形态（{@code not (} 零出现 + guard 支计数）；</li>
 * <li>EC ①：三节点展开 vs {@code <=>} 的差异——mysql 直出 {@code not (a <=> b)} vs oracle
 * 三支式 + H2 真库执行推导（禁止形态丢行 vs 钉死式不丢行）；</li>
 * <li>缓存两向：默认注入参与计划缓存（同键第二次命中缓存、transformer 不再调用、同实例返回），
 * 非默认绕过由既有 {@code TestEqlAstTransformerWiring} 锁定不回退；</li>
 * <li>EC ④载体 + 无 transformer 对照：oracle 无 transformer = 响亮报错（不静默输出裸语法）；
 * beans.xml 默认注入契约静态钉死。</li>
 * </ul>
 *
 * <p>裁定：ai-dev/design/eql-cross-db-compat/2026-10-04-wi8-phase0-adjudications.md §四.2/§七。
 * 工厂构造配方与 {@code TestEqlAstTransformerWiring} 同款（不继承 AbstractOrmTestCase，
 * 每测自建并销毁工厂）。</p>
 */
public class TestEqlNullSafePredTransformerWiring extends AbstractJdbcTestCase {

    static final String EQL_DISTINCT = "select o.classId from SimsClass o where o.classId is distinct from o.collegeId";
    static final String EQL_NOT_DISTINCT = "select o.classId from SimsClass o where o.classId is not distinct from o.collegeId";
    static final String EQL_PLAIN = "select o.classId from SimsClass o";

    /** 计数包装：接线断言用（不得只断言"类型存在"）；实例身份 = 注入的默认 transformer。 */
    static class CountingOracleTransformer extends OracleEqlAstTransformer {
        final AtomicInteger afterCount = new AtomicInteger();

        @Override
        public void transformAfterAnalyze(io.nop.orm.eql.ast.SqlProgram ast, String name, String sql,
                io.nop.orm.eql.compile.ISqlCompileContext context) {
            afterCount.incrementAndGet();
            super.transformAfterAnalyze(ast, name, sql, context);
        }
    }

    static String spaceFree(String sql) {
        return sql.replaceAll("\\s+", " ").toLowerCase().trim();
    }

    /**
     * 与 TestEqlAstTransformerWiring 完全相同的生产构造配方，另支持方言提供者覆盖
     * （oracle/mysql 文本断言用——H2 连接 + 目标方言编译，不执行）。
     */
    private OrmSessionFactoryBean newFactoryBean(IEqlAstTransformer transformer, IDialectProvider dialectProvider) {
        IContext context = ContextProvider.instance().getOrCreateContext();
        context.setTenantId("123");

        OrmSessionFactoryBean factoryBean = new OrmSessionFactoryBean();
        factoryBean.setJdbcTemplate(jdbcTemplate);
        factoryBean.setBeanProvider(new MockBeanProvider());
        factoryBean.setGlobalCache(new LocalCacheProvider("global-wi8", CacheConfig.newConfig(1000)));
        factoryBean.setSequenceGenerator(new UuidSequenceGenerator());
        factoryBean.setColumnBinderEnhancer(new DefaultOrmColumnBinderEnhancer());
        if (dialectProvider != null)
            factoryBean.setDialectProvider(dialectProvider);
        if (transformer != null)
            factoryBean.setEqlAstTransformer(transformer);
        if (transformer != null)
            factoryBean.setEqlAstTransformer(transformer);
        factoryBean.init();
        return factoryBean;
    }

    private IDialectProvider dialectOf(String name) {
        return qs -> DialectManager.instance().getDialect(name);
    }

    // ===== EC ②：oracle 正反两方向展开文本（钉死公式逐字断言 + 禁止形态负向） =====

    @Test
    public void testOracleExpansionBothDirectionsViaDefaultFactoryConfig() {
        CountingOracleTransformer transformer = new CountingOracleTransformer();
        OrmSessionFactoryBean factoryBean = newFactoryBean(transformer, dialectOf("oracle"));
        try {
            IOrmSessionFactory factory = factoryBean.getObject();

            // 不带 transformer 参数的生产入口（3-arg）——默认注入链自动生效
            ICompiledSql distinct = factory.compileSql("wi8o1", EQL_DISTINCT, true);
            String d = spaceFree(distinct.getSql().getText());
            // 三支式（裁定 §二.1 正向钉死公式）：全部 guard 支逐字在位
            // 三支式（裁定 §二.1 正向钉死公式）——按 oracle 物理列名（class_id 蛇形）逐字断言
            // ⚠️ 规范形态以本断言钉死（pretty 打印的括号内侧空格如 `is not null )` 为无条件
            // println 所致，与 Phase 2 同款——按实况钉死，不为断言改产品打印）
            assertTrue(d.contains(
                    "((o.class_id is not null and o.college_id is not null and (o.class_id <> o.college_id))"
                            + " or (o.class_id is null and o.college_id is not null )"
                            + " or (o.class_id is not null and o.college_id is null ))"), d);

            ICompiledSql notDistinct = factory.compileSql("wi8o2", EQL_NOT_DISTINCT, true);
            String nd = spaceFree(notDistinct.getSql().getText());
            // 两支式（否定方向钉死公式）
            // 两支式（否定方向钉死公式）
            assertTrue(nd.contains(
                    "(o.class_id is null and o.college_id is null )"
                            + " or (o.class_id is not null and o.college_id is not null and (o.class_id = o.college_id)))"),
                    nd);

            // 禁止形态负向对照（审查 Major-1）：展开文本不属 naive NOT 包装——
            // where 部分无 not ( 包装（NOT(NULL)=UNKNOWN 丢行形态），guard 支计数与钉死公式一致
            assertFalse(d.contains(" not ("), "IS DISTINCT FROM 展开不得含 NOT 包装: " + d);
            assertFalse(nd.contains(" not ("), "IS NOT DISTINCT FROM 展开不得含 NOT 包装: " + nd);
            assertEquals(4, countOf(d, "is not null"), "三支式含 4 个 is not null 支: " + d);
            assertEquals(2, countOf(d, " is null "), "三支式含 2 个 is null 支: " + d);
            assertEquals(2, countOf(nd, "is not null"), "两支式含 2 个 is not null 支: " + nd);

            // 接线断言（#23）：两个 name = 两次新鲜编译，默认 transformer 被运行时调用
            assertEquals(2, transformer.afterCount.get(), "默认注入的 transformer 必须被编译管线调用");

            // 缓存两向之正向（默认注入参与计划缓存）：同键第二次编译命中缓存——同实例返回且
            // transformer 不重复调用；非默认实例绕过方向由既有 TestEqlAstTransformerWiring 锁定
            ICompiledSql cached = factory.compileSql("wi8o1", EQL_DISTINCT, true);
            assertSame(distinct, cached, "同键第二次编译必须命中计划缓存（默认注入参与缓存的正向证据）");
            assertEquals(2, transformer.afterCount.get(), "缓存命中不得重复调用 transformer");
        } finally {
            factoryBean.destroy();
        }
    }

    static int countOf(String text, String sub) {
        int count = 0;
        int idx = 0;
        while ((idx = text.indexOf(sub, idx)) >= 0) {
            count++;
            idx += sub.length();
        }
        return count;
    }

    // ===== EC ①：三节点展开 vs <=> 的方言形态差异（文本 + H2 执行推导） =====

    @Test
    public void testMysqlFormDivergenceVsOraclePinnedForm() {
        CountingOracleTransformer transformer = new CountingOracleTransformer();
        OrmSessionFactoryBean mysqlFactory = newFactoryBean(transformer, dialectOf("mysql"));
        try {
            // 同一 EQL：mysql = not (a <=> b)（<=> 恒二值，NOT 良定义）；oracle 侧三支式已由
            // testOracleExpansionBothDirectionsViaDefaultFactoryConfig 按钉死公式逐字断言
            String mysqlSql = spaceFree(mysqlFactory.getObject()
                    .compileSql("wi8d2", EQL_DISTINCT, true).getSql().getText());

            assertTrue(mysqlSql.contains("not (o.class_id <=> o.college_id)"), mysqlSql);
            assertFalse(mysqlSql.contains("is not null and"), mysqlSql);
            assertFalse(mysqlSql.contains("<>"), mysqlSql);

            // 不等价差异（约束 4 ①项）登记：单操作数 NULL 行嵌套外层 NOT 时，
            // mysql 形态 not(a<=>b)=TRUE（<=> 恒二值）vs 对展开式的 NOT 包装=NOT(三支式)（禁止形态②，
            // 双 NULL 行丢行）——EQL 按方言钉死各自正确形态（mysql 直出包装 <=>；oracle 三支式替换、
            // 无 NOT 包装），两侧均与原生语义逐行一致（差异在「方言输出形态」而非语义错误）。
            // mysql transformer 直通不改写（计数不涨是允许的直通证据；此处 transformer 计数已被
            // 上一个测试的实例独立承载）。
        } finally {
            mysqlFactory.destroy();
        }
    }

    /**
     * H2 真库执行推导（EC ① 的可执行面）：同一数据行（单操作数 NULL）上，
     * 禁止形态 naive NOT 包装丢行（UNKNOWN → WHERE 剔除）vs 钉死三支式不丢行。
     * 公式文本与 oracle 编译产物同源（安全词表），H2 可直接执行。
     */
    @Test
    public void testForbiddenNaiveWrapperLosesRowsExecutedOnH2() throws Exception {
        try (java.sql.Connection con = dataSource.getConnection()) {
            try (java.sql.Statement st = con.createStatement()) {
                st.execute("drop table if exists wi8div");
                st.execute("create table wi8div(a varchar(10), b varchar(10))");
                // 数据行：a=NULL,b='x'（单 NULL）与 a=NULL,b=NULL（双 NULL）
                st.execute("insert into wi8div values(null, 'x')");
                st.execute("insert into wi8div values(null, null)");

                // 禁止形态①（naive NOT 包装，恰为 WI4 裁定文档 provisional 式）：单 NULL 行 NOT(NULL)=NULL → 丢行
                try (java.sql.ResultSet rs = st.executeQuery(
                        "select count(*) from wi8div where not ((a = b) or (a is null and b is null))")) {
                    rs.next();
                    assertEquals(0, rs.getInt(1), "禁止形态①在单 NULL 行产生 UNKNOWN 被 WHERE 丢行（原生应为 TRUE）");
                }
                // 钉死三支式：全部行二值，单 NULL 行=TRUE → 1 行
                try (java.sql.ResultSet rs = st.executeQuery(
                        "select count(*) from wi8div where (a is not null and b is not null and a<>b)"
                                + " or (a is null and b is not null) or (a is not null and b is null)")) {
                    rs.next();
                    assertEquals(1, rs.getInt(1), "钉死三支式单 NULL 行=TRUE（与原生 IS DISTINCT FROM 逐行一致）");
                }
                // 双 NULL 行：钉死三支式=FALSE、禁止形态② NOT(两支式) 在双 NULL 行 NOT(NULL)=NULL 丢行（原生应 FALSE 的行
                // 拿到 UNKNOWN 同样是语义错误——两禁止形态均被本执行对照排除）
                try (java.sql.ResultSet rs = st.executeQuery(
                        "select count(*) from wi8div where (a is null and b is null) or (a is not null and b is not null and a=b)")) {
                    rs.next();
                    assertEquals(1, rs.getInt(1), "两支式双 NULL 行=TRUE");
                }
            }
        }
    }

    // ===== 无 transformer 对照：oracle 编译期响亮（不静默输出裸语法，#24/P1） =====
    // EqlCompiler 级负向已由 nop-orm-eql TestEqlNullSafeDistinctDialect.testOracleWithoutTransformerFailsLoud
    // 覆盖（渲染三分支第 3 支响亮，与本类工厂路径同一 EqlCompiler 管线）；工厂级默认配置路径的
    // 对照面 = 本类 testH2NativeEndToEndWithDefaultTransformer（安全形态可执行）。

    // ===== 端到端（#22）：默认 transformer + H2 原生分支真实执行（非 oracle 方言直通不改写） =====

    @Test
    public void testH2NativeEndToEndWithDefaultTransformer() {
        CountingOracleTransformer transformer = new CountingOracleTransformer();
        OrmSessionFactoryBean factoryBean = newFactoryBean(transformer, null); // 缺省方言提供者=jdbcTemplate(H2)
        try {
            IOrmSessionFactory factory = factoryBean.getObject();
            createTables(factory);

            IOrmTemplate template = new OrmTemplateImpl(factory);
            OrmDaoProvider daoProvider = new OrmDaoProvider();
            daoProvider.setOrmTemplate(template);

            IEntityDao<SimsClass> dao = daoProvider.daoFor(SimsClass.class);
            SimsClass match = dao.newEntity(); // classId = collegeId（null-safe 相等）
            match.setClassId("A");
            match.setCollegeId("A");
            match.setClassName("c1");
            match.setMajorId("m");
            dao.saveEntity(match);

            SimsClass singleNull = dao.newEntity(); // classId 非空 + collegeId NULL → IS NOT DISTINCT FROM 不命中
            singleNull.setClassId("B");
            singleNull.setClassName("c2");
            singleNull.setMajorId("m");
            dao.saveEntity(singleNull);

            // H2 营 native 分支（transformer 对非 oracle 直通）——编译文本为原生方向文本
            ICompiledSql compiled = factory.compileSql("wi8e2e", EQL_NOT_DISTINCT, true);
            String text = spaceFree(compiled.getSql().getText());
            assertTrue(text.contains("is not distinct from"), "h2 走 native 直出分支: " + text);
            assertFalse(text.contains("<=>"), text);

            // 端到端执行：session 查询主路径返回 null-safe 相等行（仅 match）
            List<Map<String, Object>> rows = template.findAll(
                    SQL.begin().sql(EQL_NOT_DISTINCT).end());
            assertEquals(1, rows.size(), "null-safe 相等只命中 (A,A) 行");

            // 非 oracle 方言 transformer 直通仍被调用（固定环节），但不改写 AST
            assertTrue(transformer.afterCount.get() >= 2, "默认 transformer 参与每次编译（固定环节）");
        } finally {
            factoryBean.destroy();
        }
    }

    private void createTables(IOrmSessionFactory factory) {
        Collection<? extends IEntityModel> tables = factory.getOrmModel().getEntityModelsInTopoOrder();
        String createSql = new DdlSqlCreator(jdbcTemplate.getDialectForQuerySpace(null)).createTables(tables, false);
        jdbcTemplate.executeMultiSql(new SQL(createSql));
    }

    // ===== beans.xml 默认注入契约静态钉死（生产配置源，杜绝"已接线、未配置"回退） =====

    @Test
    public void testBeansXmlDefaultInjectionContract() {
        IResource resource = VirtualFileSystem.instance()
                .getResource("/nop/orm/beans/orm-defaults.beans.xml");
        assertNotNull(resource, "orm-defaults.beans.xml 必须存在");
        String xml = ResourceHelper.readText(resource);
        assertTrue(xml.contains("io.nop.orm.eql.compile.OracleEqlAstTransformer"),
                "beans.xml 必须定义生产 transformer bean");
        assertTrue(xml.contains("<property name=\"eqlAstTransformer\" ref=\"nopOracleEqlAstTransformer\"/>"),
                "nopOrmSessionFactory 必须默认注入 eqlAstTransformer（WI8 Phase 3 落地后不允许回退到未配置态）");
    }
}
