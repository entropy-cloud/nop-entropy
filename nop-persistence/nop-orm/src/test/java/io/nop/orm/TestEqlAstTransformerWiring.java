/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm;

import io.nop.api.core.context.ContextProvider;
import io.nop.api.core.context.IContext;
import io.nop.app.SimsClass;
import io.nop.commons.cache.CacheConfig;
import io.nop.commons.cache.LocalCacheProvider;
import io.nop.core.lang.sql.SQL;
import io.nop.dao.api.IEntityDao;
import io.nop.dao.seq.UuidSequenceGenerator;
import io.nop.orm.dao.OrmDaoProvider;
import io.nop.orm.ddl.DdlSqlCreator;
import io.nop.orm.eql.ICompiledSql;
import io.nop.orm.eql.IEqlAstTransformer;
import io.nop.orm.eql.ast.SqlAlias;
import io.nop.orm.eql.ast.SqlExprProjection;
import io.nop.orm.eql.ast.SqlProgram;
import io.nop.orm.eql.ast.SqlProjection;
import io.nop.orm.eql.ast.SqlSelect;
import io.nop.orm.eql.compile.ISqlCompileContext;
import io.nop.orm.factory.DefaultOrmColumnBinderEnhancer;
import io.nop.orm.factory.OrmSessionFactoryBean;
import io.nop.orm.impl.OrmTemplateImpl;
import io.nop.orm.mock.MockBeanProvider;
import io.nop.orm.model.IEntityModel;
import io.nop.orm.persister.IPersistEnv;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WI4（ai-dev/plans/21-eql-wi4-ast-transformer-wiring.md）的两个证明测试：
 *
 * <p>1. {@link #testDefaultTransformerFromFactoryConfigEndToEnd}：经生产配置路径（{@code OrmSessionFactoryBean
 * init()} 前调用 {@code setEqlAstTransformer}）注入 transformer 后，不带 transformer 参数的各入口
 * （3-arg {@code IOrmSessionFactory.compileSql} / 5-arg {@code IPersistEnv.compileSql} /
 * {@code ILoadedOrmModel} 3-arg default 方法的 null 回落）都能拿到 lowering，并断言 transformer
 * 两个钩子在运行时确实被编译管线调用。必须自建 OrmSessionFactoryBean：
 * {@code AbstractOrmTestCase.setUp()} 自己调用 {@code init()}，继承基类无法在 init 前注入；且
 * {@code DefaultOrmModelProvider} 构造时以固定名注册全局缓存，同一测试内不允许并存第二个基类工厂，
 * 故本测试类不继承 AbstractOrmTestCase，每个测试自行构建并销毁工厂。</p>
 *
 * <p>2. {@link #testNonDefaultTransformerBypassesQueryPlanCache}：同一 querySpace、同
 * name+sqlText+flags 下，语义不一致的 transformer 编译（useCache=true）绝不返回过期 SQL——
 * 修复前该测试红（第二个 transformer 命中第一个 transformer 写入缓存键的计划）。
 * 裁定：ai-dev/design/eql-cross-db-compat/2026-10-04-wi4-phase0-adjudications.md（候选 B：
 * 非默认 transformer 绕过缓存，不读不写）。</p>
 *
 * <p>lowering 标记实现：改写已解析投影的 alias（参数保持型纯语法改写，after 槽位安全——
 * 新增未解析节点在 after 槽位会被 AstToSqlGenerator 的 resolvedExprMeta 解引用拒绝）。</p>
 */
public class TestEqlAstTransformerWiring extends AbstractJdbcTestCase {

    static final String EQL = "select o.classId as cid from SimsClass o";

    /**
     * 测试桩：afterAnalyze 槽位给第一个带别名的投影追加可识别后缀。两个钩子都计数，
     * 用于接线断言（不得只断言"类型存在"）。找不到可改写目标时响亮失败，不静默跳过。
     */
    static class MarkerTransformer implements IEqlAstTransformer {
        final String marker;
        final AtomicInteger beforeCount = new AtomicInteger();
        final AtomicInteger afterCount = new AtomicInteger();

        MarkerTransformer(String marker) {
            this.marker = marker;
        }

        @Override
        public void transformBeforeAnalyze(SqlProgram ast, String name, String sql, ISqlCompileContext context) {
            beforeCount.incrementAndGet();
        }

        @Override
        public void transformAfterAnalyze(SqlProgram ast, String name, String sql, ISqlCompileContext context) {
            afterCount.incrementAndGet();
            applyMarker(ast);
        }

        void applyMarker(SqlProgram ast) {
            SqlSelect select = (SqlSelect) ast.getStatements().get(0);
            for (SqlProjection proj : select.getProjections()) {
                if (proj instanceof SqlExprProjection && ((SqlExprProjection) proj).getAlias() != null) {
                    SqlAlias alias = ((SqlExprProjection) proj).getAlias();
                    alias.setAlias(alias.getAlias() + marker);
                    return;
                }
            }
            throw new IllegalStateException(
                    "no aliased expr projection in compiled statement, cannot apply marker: " + marker);
        }
    }

    /**
     * 与 {@code AbstractOrmTestCase.setUp()} 完全相同的生产构造配方，仅在 init() 前多一步可选的
     * transformer 配置注入（生产路径：SessionFactoryConfig.eqlAstTransformer → init → SessionFactoryImpl
     * .defaultAstTransformer）。
     */
    private OrmSessionFactoryBean newFactoryBean(IEqlAstTransformer transformer) {
        IContext context = ContextProvider.instance().getOrCreateContext();
        context.setTenantId("123");

        OrmSessionFactoryBean factoryBean = new OrmSessionFactoryBean();
        factoryBean.setJdbcTemplate(jdbcTemplate);
        factoryBean.setBeanProvider(new MockBeanProvider());
        factoryBean.setGlobalCache(new LocalCacheProvider("global-wi4", CacheConfig.newConfig(1000)));
        factoryBean.setSequenceGenerator(new UuidSequenceGenerator());
        factoryBean.setColumnBinderEnhancer(new DefaultOrmColumnBinderEnhancer());
        if (transformer != null)
            factoryBean.setEqlAstTransformer(transformer);
        factoryBean.init();
        return factoryBean;
    }

    /**
     * 证明测试 1（端到端 + 接线）：factory 级默认配置 → 不带 transformer 参数的入口 → 生成 SQL 含 lowering；
     * 计数器断言 transformer 方法确实被运行时调用；最后经 JdbcQueryExecutor 路径真实执行，证明端到端可达。
     */
    @Test
    public void testDefaultTransformerFromFactoryConfigEndToEnd() {
        MarkerTransformer transformer = new MarkerTransformer("_wi4_default");
        OrmSessionFactoryBean factoryBean = newFactoryBean(transformer);
        try {
            IOrmSessionFactory factory = factoryBean.getObject();

            createTables(factory);

            IOrmTemplate template = new OrmTemplateImpl(factory);
            OrmDaoProvider daoProvider = new OrmDaoProvider();
            daoProvider.setOrmTemplate(template);

            // 入口1：IOrmSessionFactory 3-arg（SessionFactoryImpl 注入字段默认实例）
            ICompiledSql c1 = factory.compileSql("wi4e1", EQL, true);
            assertTrue(c1.getSql().getText().contains("cid_wi4_default"),
                    "3-arg sessionFactory.compileSql should carry default transformer lowering: "
                            + c1.getSql().getText());

            // 入口2：IPersistEnv 5-arg（JdbcQueryExecutor 的实际路径）
            IPersistEnv env = (IPersistEnv) factory;
            ICompiledSql c2 = env.compileSql("wi4e2", EQL, true, false, false);
            assertTrue(c2.getSql().getText().contains("cid_wi4_default"),
                    "5-arg env.compileSql should carry default transformer lowering: " + c2.getSql().getText());

            // 入口3：ILoadedOrmModel 3-arg default 方法（显式 null → LoadedOrmModel 回落 env 默认）
            ICompiledSql c3 = env.getLoadedOrmModel().compileSql("wi4e3", EQL, true);
            assertTrue(c3.getSql().getText().contains("cid_wi4_default"),
                    "ILoadedOrmModel null-fallback path should carry default transformer lowering: "
                            + c3.getSql().getText());

            // 接线断言：3 个不同 name = 3 次新鲜编译，两个钩子各被调用 3 次
            assertEquals(3, transformer.beforeCount.get());
            assertEquals(3, transformer.afterCount.get());

            // 端到端：session 查询主路径真实执行 lowering 后的 SQL（无 name 的新缓存键 → 第 4 次新鲜编译）
            IEntityDao<SimsClass> dao = daoProvider.daoFor(SimsClass.class);
            SimsClass entity = dao.newEntity();
            entity.setClassId("11");
            entity.setCollegeId("1");
            entity.setClassName("classA");
            entity.setMajorId("a");
            dao.saveEntity(entity);

            List<Map<String, Object>> rows = template.findAll(SQL.begin().sql(EQL).end());
            assertTrue(rows.size() > 0, "lowered sql should execute and return rows");
            assertEquals(4, transformer.beforeCount.get());
            assertEquals(4, transformer.afterCount.get());
        } finally {
            factoryBean.destroy();
        }
    }

    private void createTables(IOrmSessionFactory factory) {
        Collection<? extends IEntityModel> tables = factory.getOrmModel().getEntityModelsInTopoOrder();
        String createSql = new DdlSqlCreator(jdbcTemplate.getDialectForQuerySpace(null)).createTables(tables, false);
        jdbcTemplate.executeMultiSql(new SQL(createSql));
    }

    /**
     * 证明测试 2（缓存安全）：同 querySpace、同 name+sqlText+flags 下，非默认 transformer 各自新鲜编译，
     * 绝不命中彼此写入缓存键的计划；无 transformer 的默认路径不含任何标记。
     */
    @Test
    public void testNonDefaultTransformerBypassesQueryPlanCache() {
        OrmSessionFactoryBean factoryBean = newFactoryBean(null);
        try {
            IOrmSessionFactory factory = factoryBean.getObject();

            MarkerTransformer transformerA = new MarkerTransformer("_wi4_a");
            MarkerTransformer transformerB = new MarkerTransformer("_wi4_b");

            // A 编译（useCache=true）
            ICompiledSql compiledA = factory.compileSql("wi4cache", EQL, true, transformerA, true, false, false);
            assertTrue(compiledA.getSql().getText().contains("cid_wi4_a"),
                    "A's own compile must contain A marker: " + compiledA.getSql().getText());

            // B 编译（useCache=true，同键）：修复前命中 A 写入的过期计划（红）；修复后绕过缓存新鲜编译（绿）
            ICompiledSql compiledB = factory.compileSql("wi4cache", EQL, true, transformerB, true, false, false);
            assertTrue(compiledB.getSql().getText().contains("cid_wi4_b"),
                    "B compile must contain B marker, got: " + compiledB.getSql().getText());
            assertFalse(compiledB.getSql().getText().contains("cid_wi4_a"),
                    "B compile must never return A's cached plan: " + compiledB.getSql().getText());

            // 无 transformer 的默认路径：不含任何标记
            ICompiledSql compiledDefault = factory.compileSql("wi4cache", EQL, true, null, true, false, false);
            assertFalse(compiledDefault.getSql().getText().contains("cid_wi4_a"),
                    "default compile must not contain A marker: " + compiledDefault.getSql().getText());
            assertFalse(compiledDefault.getSql().getText().contains("cid_wi4_b"),
                    "default compile must not contain B marker: " + compiledDefault.getSql().getText());
            assertTrue(compiledDefault.getSql().getText().contains(" cid "),
                    "default compile keeps original alias: " + compiledDefault.getSql().getText());

            // 非默认 transformer 各自新鲜编译：B 至少被真实编译一次（A 的计划不因缓存复用而拦截 B 的编译）
            assertEquals(1, transformerB.afterCount.get());
        } finally {
            factoryBean.destroy();
        }
    }
}
