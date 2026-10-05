package io.nop.orm.eql.compile;

import io.nop.commons.type.StdSqlType;
import io.nop.dao.dialect.IDialect;
import io.nop.dao.dialect.impl.DialectImpl;
import io.nop.dao.dialect.model.DialectFeatures;
import io.nop.dao.dialect.model.DialectModel;
import io.nop.dao.dialect.model.DialectSqls;
import io.nop.dao.dialect.model.SqlNativeFunctionModel;
import io.nop.orm.eql.ICompiledSql;
import io.nop.orm.eql.IEqlAstTransformer;
import io.nop.orm.eql.SqlOrderByOrdinal;
import io.nop.orm.eql.ast.SqlStatementKind;
import io.nop.orm.eql.meta.EntityTableMeta;
import io.nop.orm.eql.meta.ISqlTableMeta;
import io.nop.orm.eql.sql.IAliasGenerator;
import io.nop.orm.eql.sql.SeqAliasGenerator;
import io.nop.orm.model.OrmColumnModel;
import io.nop.orm.model.OrmEntityModel;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * plan 2321 Phase 1 focused 测试：CompiledSql 编译期结构信息暴露。
 *
 * <p>覆盖：六个构造标志（hasGroupBy/hasHaving/hasDistinct/hasSetOp/hasForUpdate/isBareCount）
 * 各正/反断言、orderBy→投影序数映射（已投影→序数与方向正确、别名引用→映射成功、
 * 裸表达式重复/未投影→无映射项、多排序项）、非 SELECT 语句确定缺省值。</p>
 *
 * <p>基于最小实体模型的端到端编译（对齐 {@link TestEqlCompileSql} 先例）：
 * EqlCompiler -> EqlTransformVisitor -> AstToSqlGenerator。</p>
 */
public class TestCompiledSqlStructFlags {
    static IDialect dialect;

    static TestCompileContext context;

    @BeforeAll
    public static void init() {
        // 模块测试环境未注册dialect.xml组件加载器，直接构造最小测试方言
        DialectModel model = new DialectModel();
        DialectFeatures features = new DialectFeatures();
        features.setSupportReturningForUpdate(true);
        model.setFeatures(features);
        model.setReservedKeywords(Collections.emptySet());
        DialectSqls sqls = new DialectSqls();
        // for update 锁模板非空：空模板方言在编译期报 dialect-not-support-feature
        sqls.setForUpdate(" for update ");
        model.setSqls(sqls);
        model.setSqlDataTypes(Collections.emptyList());

        SqlNativeFunctionModel currentTimestamp = new SqlNativeFunctionModel();
        currentTimestamp.setName("current_timestamp");
        currentTimestamp.setHasParenthesis(false);
        currentTimestamp.setReturnType(StdSqlType.TIMESTAMP);

        SqlNativeFunctionModel upper = new SqlNativeFunctionModel();
        upper.setName("upper");
        upper.setHasParenthesis(true);
        upper.setArgTypes(Collections.singletonList(StdSqlType.VARCHAR));
        upper.setReturnType(StdSqlType.VARCHAR);
        model.setFunctions(Arrays.asList(currentTimestamp, upper));

        dialect = new DialectImpl(model);

        context = new TestCompileContext();
        context.entities.put("AppUser", entity("AppUser", "APP_USER", "spaceA",
                col("id", "SID", 1, true), col("name", "NAME", 2, false)));
        context.entities.put("AppRole", entity("AppRole", "APP_ROLE", "spaceA",
                col("id", "SID", 1, true), col("userId", "USER_ID", 2, false)));
    }

    static ICompiledSql compile(String eql) {
        return new EqlCompiler().compile("test", eql, context);
    }

    static OrmColumnModel col(String name, String code, int propId, boolean primary) {
        OrmColumnModel c = new OrmColumnModel();
        c.setName(name);
        c.setCode(code);
        c.setPropId(propId);
        c.setStdSqlType(StdSqlType.VARCHAR);
        c.setPrimary(primary);
        return c;
    }

    static OrmEntityModel entity(String name, String tableName, String querySpace, OrmColumnModel... cols) {
        OrmEntityModel m = new OrmEntityModel();
        m.setName(name);
        m.setTableName(tableName);
        m.setQuerySpace(querySpace);
        m.setColumns(new ArrayList<>(Arrays.asList(cols)));
        m.init();
        return m;
    }

    static class TestCompileContext implements ISqlCompileContext {
        final Map<String, OrmEntityModel> entities = new HashMap<>();

        @Override
        public boolean isDisableLogicalDelete() {
            return false;
        }

        @Override
        public boolean isAllowUnderscoreName() {
            return false;
        }

        @Override
        public boolean isEnableFilter() {
            return false;
        }

        @Override
        public IEqlAstTransformer getAstTransformer() {
            return null;
        }

        @Override
        public IDialect getDialectForQuerySpace(String querySpace) {
            return dialect;
        }

        @Override
        public ISqlTableMeta resolveEntityTableMeta(String entityName) {
            OrmEntityModel m = entities.get(entityName);
            if (m == null)
                return null;
            return new EntityTableMeta(m, null, getDialectForQuerySpace(m.getQuerySpace()));
        }

        @Override
        public IAliasGenerator getAliasGenerator() {
            return new SeqAliasGenerator();
        }
    }

    // ---------- 无构造查询：全标志缺省 + 无 orderBy ----------

    @Test
    public void testPlainSelectAllFlagsDefault() {
        ICompiledSql compiled = compile("select o.name from AppUser o");
        assertFalse(compiled.hasGroupBy());
        assertFalse(compiled.hasHaving());
        assertFalse(compiled.hasDistinct());
        assertFalse(compiled.hasSetOp());
        assertFalse(compiled.hasForUpdate());
        assertFalse(compiled.isBareCount());
        assertFalse(compiled.hasAggregate());
        assertTrue(compiled.getOrderByOrdinals().isEmpty());
    }

    // ---------- 六标志正/反断言 ----------

    @Test
    public void testGroupByAndHavingFlags() {
        ICompiledSql compiled = compile("select o.name, count(1) from AppUser o group by o.name having count(1) > 0");
        assertTrue(compiled.hasGroupBy());
        assertTrue(compiled.hasHaving());
        // 带分组聚合不是裸 count
        assertFalse(compiled.isBareCount());

        ICompiledSql noHaving = compile("select o.name, count(1) from AppUser o group by o.name");
        assertTrue(noHaving.hasGroupBy());
        assertFalse(noHaving.hasHaving());
    }

    @Test
    public void testDistinctFlag() {
        ICompiledSql compiled = compile("select distinct o.name from AppUser o");
        assertTrue(compiled.hasDistinct());

        ICompiledSql plain = compile("select o.name from AppUser o");
        assertFalse(plain.hasDistinct());
    }

    @Test
    public void testSetOpFlag() {
        ICompiledSql compiled = compile("select o.id from AppUser o union select r.id from AppRole r");
        assertTrue(compiled.hasSetOp());

        ICompiledSql unionAll = compile("select o.id from AppUser o union all select r.id from AppRole r");
        assertTrue(unionAll.hasSetOp());

        ICompiledSql plain = compile("select o.id from AppUser o");
        assertFalse(plain.hasSetOp());
    }

    @Test
    public void testForUpdateFlag() {
        ICompiledSql compiled = compile("select o.id from AppUser o where o.name = 'a' for update");
        assertTrue(compiled.hasForUpdate());

        ICompiledSql plain = compile("select o.id from AppUser o where o.name = 'a'");
        assertFalse(plain.hasForUpdate());
    }

    // ---------- 裸 count 判定 ----------

    @Test
    public void testBareCountPositiveForms() {
        ICompiledSql star = compile("select count(*) from AppUser o");
        assertTrue(star.isBareCount());
        assertEquals(1, star.getFieldMetas().size());

        ICompiledSql literal = compile("select count(1) from AppUser o");
        assertTrue(literal.isBareCount());

        ICompiledSql column = compile("select count(o.id) from AppUser o");
        assertTrue(column.isBareCount());
    }

    @Test
    public void testBareCountNegativeForms() {
        // count(distinct x)：各分片求和≠全局去重数，不算裸 count
        ICompiledSql distinctCount = compile("select count(distinct o.name) from AppUser o");
        assertFalse(distinctCount.isBareCount());

        // 分组聚合
        ICompiledSql grouped = compile("select o.name, count(1) from AppUser o group by o.name");
        assertFalse(grouped.isBareCount());

        // 非聚合投影
        ICompiledSql plain = compile("select o.name from AppUser o");
        assertFalse(plain.isBareCount());
    }

    // ---------- orderBy→投影序数映射 ----------

    @Test
    public void testOrderByProjectedOrdinalAndDirection() {
        ICompiledSql compiled = compile("select o.id, o.name from AppUser o order by o.name desc nulls first, o.id asc");
        List<SqlOrderByOrdinal> ordinals = compiled.getOrderByOrdinals();
        assertEquals(2, ordinals.size());

        // 第一排序项：o.name → 投影序数1，desc，nulls first
        assertEquals(1, ordinals.get(0).getOrdinal());
        assertFalse(ordinals.get(0).isAsc());
        assertEquals(Boolean.TRUE, ordinals.get(0).getNullsFirst());

        // 第二排序项：o.id → 投影序数0，asc，未声明 nulls（三态null）
        assertEquals(0, ordinals.get(1).getOrdinal());
        assertTrue(ordinals.get(1).isAsc());
        assertNull(ordinals.get(1).getNullsFirst());
    }

    @Test
    public void testOrderByAliasReferenceMapsToProjection() {
        // 别名引用：排序键经 resolveSelectExpr 解析到投影表达式，映射成功
        ICompiledSql compiled = compile("select o.name as nm from AppUser o order by nm");
        List<SqlOrderByOrdinal> ordinals = compiled.getOrderByOrdinals();
        assertEquals(1, ordinals.size());
        assertEquals(0, ordinals.get(0).getOrdinal());
        assertTrue(ordinals.get(0).isAsc());
    }

    @Test
    public void testOrderByNotProjectedNoMapping() {
        // 排序键未投影：条目存在但无有效映射（序数哨兵值），消费方据此显式失败
        ICompiledSql compiled = compile("select o.id from AppUser o order by o.name");
        List<SqlOrderByOrdinal> ordinals = compiled.getOrderByOrdinals();
        assertEquals(1, ordinals.size());
        assertEquals(SqlOrderByOrdinal.ORDINAL_NOT_PROJECTED, ordinals.get(0).getOrdinal());
        assertFalse(ordinals.get(0).isProjected());
    }

    @Test
    public void testOrderByBareDuplicatedExprNoMapping() {
        // 裸表达式重复形态：重复投影列的表达式是独立节点，无投影映射 → 显式失败路径
        ICompiledSql compiled = compile("select o.name from AppUser o order by upper(o.name)");
        List<SqlOrderByOrdinal> ordinals = compiled.getOrderByOrdinals();
        assertEquals(1, ordinals.size());
        assertEquals(SqlOrderByOrdinal.ORDINAL_NOT_PROJECTED, ordinals.get(0).getOrdinal());
    }

    // ---------- 聚合投影标志（实施期补充的第七标志） ----------

    @Test
    public void testAggregateFlag() {
        // 无 GROUP BY 的非count聚合：六标志欠定，由 hasAggregate 闭合"非count聚合显式失败"判定面
        ICompiledSql sum = compile("select sum(o.id) from AppUser o");
        assertTrue(sum.hasAggregate());
        assertFalse(sum.isBareCount());
        assertFalse(sum.hasGroupBy());

        // 裸 count 也是聚合，但由 isBareCount 放行
        ICompiledSql count = compile("select count(*) from AppUser o");
        assertTrue(count.hasAggregate());
        assertTrue(count.isBareCount());

        ICompiledSql plain = compile("select o.name from AppUser o");
        assertFalse(plain.hasAggregate());
    }

    @Test
    public void testAggregateInsideSubQueryNotFlagged() {
        // 子查询内的聚合在每分片内独立求值，不属顶层聚合投影
        ICompiledSql compiled = compile("select o.name, (select count(*) from AppRole r) from AppUser o");
        assertFalse(compiled.hasAggregate());
    }

    // ---------- 非 SELECT 语句确定缺省值 ----------

    @Test
    public void testNonSelectStatementDefaults() {
        ICompiledSql update = compile("update AppUser set name = 'x' where id = '1'");
        assertEquals(SqlStatementKind.UPDATE, update.getStatementKind());
        assertFalse(update.hasGroupBy());
        assertFalse(update.hasHaving());
        assertFalse(update.hasDistinct());
        assertFalse(update.hasSetOp());
        assertFalse(update.hasForUpdate());
        assertFalse(update.isBareCount());
        assertFalse(update.hasAggregate());
        assertTrue(update.getOrderByOrdinals().isEmpty());

        ICompiledSql del = compile("delete from AppUser where id = '1'");
        assertEquals(SqlStatementKind.DELETE, del.getStatementKind());
        assertTrue(del.getOrderByOrdinals().isEmpty());
        assertFalse(del.hasGroupBy());
    }

    // ---------- 静态填充函数单测（两态直断） ----------

    @Test
    public void testBuildOrderByOrdinalsNullSafe() {
        // 无 orderBy / 非 SELECT（querySelect为null）：确定缺省——空列表，无null歧义
        assertTrue(EqlCompiler.buildOrderByOrdinals(null, Collections.emptyList()).isEmpty());
    }

    @Test
    public void testBareCountHelperDirectly() {
        assertTrue(EqlCompiler.isBareCountProjection(Collections.emptyList(), false, false) == false);
    }
}
