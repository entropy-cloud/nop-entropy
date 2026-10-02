/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.eql.compile;

import io.nop.api.core.exceptions.NopException;
import io.nop.commons.type.StdDataType;
import io.nop.commons.type.StdSqlType;
import io.nop.dao.dialect.IDialect;
import io.nop.dao.dialect.impl.DialectImpl;
import io.nop.dao.dialect.model.DialectModel;
import io.nop.dao.dialect.model.SqlNativeFunctionModel;
import io.nop.orm.eql.ICompiledSql;
import io.nop.orm.eql.IEqlAstTransformer;
import io.nop.orm.eql.ast.SqlStatementKind;
import io.nop.orm.eql.meta.EntityTableMeta;
import io.nop.orm.eql.meta.ISqlTableMeta;
import io.nop.orm.eql.sql.IAliasGenerator;
import io.nop.orm.eql.sql.SeqAliasGenerator;
import io.nop.orm.model.OrmColumnModel;
import io.nop.orm.model.OrmEntityModel;
import io.nop.orm.model.OrmJoinOnModel;
import io.nop.orm.model.OrmToManyReferenceModel;
import io.nop.orm.model.OrmToOneReferenceModel;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static io.nop.orm.eql.OrmEqlErrors.ERR_EQL_UNKNOWN_FUNCTION;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WI4 覆盖补强：经公开翻译入口 EqlCompiler.compile 的端到端语义用例。
 * 断言产出 SQL 文本 / 参数绑定 / 语句元数据，而不是仅构建 AST。
 * 方言为最小手工 DialectModel（模块测试环境没有 dialect.xml 组件加载器），
 * 与既有 TestEqlCompileSql 同一先例。
 */
public class TestEqlTranslationSemantics {
    static IDialect dialect;
    static TestCompileContext context;

    @BeforeAll
    public static void init() {
        dialect = buildDialect();
        context = baseContext();
    }

    static DialectModel newDialectModel() {
        DialectModel model = new DialectModel();
        model.setReservedKeywords(Collections.emptySet());
        io.nop.dao.dialect.model.DialectSqls sqls = new io.nop.dao.dialect.model.DialectSqls();
        // 无from select 需要经 selectFromDual 模板包装（与 oracle 方言同形）
        sqls.setSelectFromDual("select {fields} from dual");
        model.setSqls(sqls);
        model.setSqlDataTypes(Collections.emptyList());
        model.setFeatures(new io.nop.dao.dialect.model.DialectFeatures());
        return model;
    }

    static SqlNativeFunctionModel nativeFunc(String name, boolean hasParenthesis, StdSqlType... argTypes) {
        SqlNativeFunctionModel fn = new SqlNativeFunctionModel();
        fn.setName(name);
        fn.setHasParenthesis(hasParenthesis);
        fn.setReturnType(StdSqlType.VARCHAR);
        fn.setArgTypes(Arrays.asList(argTypes));
        return fn;
    }

    static IDialect buildDialect() {
        DialectModel model = newDialectModel();
        // 带参函数按 argTypes 校验参数个数；hasParenthesis 缺省 true
        model.setFunctions(Arrays.asList(
                // DialectImpl 构造期会解析 current_timestamp 的渲染SQL，必须注册
                nativeFunc("current_timestamp", false),
                nativeFunc("upper", true, StdSqlType.VARCHAR),
                nativeFunc("lower", true, StdSqlType.VARCHAR),
                // 无参关键字函数按无括号渲染
                nativeFunc("sys_utc_now", false),
                // 注册 ilike 原生函数后，大小写不敏感 like 应翻译为该函数（被比较列与匹配串两个参数）
                nativeFunc("ilike", true, StdSqlType.VARCHAR, StdSqlType.VARCHAR)));
        return new DialectImpl(model);
    }

    static OrmColumnModel col(String name, String code, int propId, boolean primary) {
        OrmColumnModel c = new OrmColumnModel();
        c.setName(name);
        c.setCode(code);
        c.setPropId(propId);
        c.setStdSqlType(StdSqlType.VARCHAR);
        // 参数绑定经 SimpleParamBuilder 按列的StdDataType转换输入值，必须显式设置
        c.setStdDataType(StdDataType.STRING);
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

    static TestCompileContext baseContext() {
        TestCompileContext ctx = new TestCompileContext();
        ctx.entities.put("AppUser", entity("AppUser", "APP_USER", "spaceA",
                col("id", "SID", 1, true), col("name", "NAME", 2, false),
                col("status", "STATUS", 3, false)));
        ctx.entities.put("AppRole", entity("AppRole", "APP_ROLE", "spaceA",
                col("id", "SID", 1, true), col("userId", "USER_ID", 2, false),
                col("name", "NAME", 3, false)));
        ctx.entities.put("AppDept", entity("AppDept", "APP_DEPT", "spaceA",
                col("id", "SID", 1, true), col("name", "NAME", 2, false)));
        return ctx;
    }

    /**
     * AppUser(dept -> AppDept to-one, roles -> AppRole to-many)
     */
    static TestCompileContext relationContext() {
        OrmEntityModel user = new OrmEntityModel();
        user.setName("AppUser");
        user.setTableName("APP_USER");
        user.setQuerySpace("spaceA");
        OrmColumnModel userPk = col("id", "SID", 1, true);
        OrmColumnModel userName = col("name", "NAME", 2, false);
        OrmColumnModel userDept = col("deptId", "DEPT_ID", 3, false);
        user.setColumns(new ArrayList<>(Arrays.asList(userPk, userName, userDept)));

        OrmEntityModel role = entity("AppRole", "APP_ROLE", "spaceA",
                col("id", "SID", 1, true), col("userId", "USER_ID", 2, false),
                col("name", "NAME", 3, false));
        OrmEntityModel dept = entity("AppDept", "APP_DEPT", "spaceA",
                col("id", "SID", 1, true), col("name", "NAME", 2, false));

        OrmToManyReferenceModel roles = new OrmToManyReferenceModel();
        roles.setName("roles");
        roles.setRefEntityName("AppRole");
        roles.setRefEntityModel(role);
        roles.setColumns(Collections.emptyList());
        OrmJoinOnModel roleJoin = new OrmJoinOnModel();
        roleJoin.setLeftPropModel(userPk);
        roleJoin.setRightPropModel(role.getColumn("userId", false));
        roles.setJoin(new ArrayList<>(Collections.singletonList(roleJoin)));
        user.addProp(roles);

        OrmToOneReferenceModel deptRef = new OrmToOneReferenceModel();
        deptRef.setName("dept");
        deptRef.setRefEntityName("AppDept");
        deptRef.setRefEntityModel(dept);
        deptRef.setColumns(Collections.singletonList(userDept));
        OrmJoinOnModel deptJoin = new OrmJoinOnModel();
        deptJoin.setLeftPropModel(userDept);
        deptJoin.setRightPropModel(dept.getColumn("id", false));
        deptRef.setJoin(new ArrayList<>(Collections.singletonList(deptJoin)));
        user.addProp(deptRef);

        user.init();

        TestCompileContext ctx = new TestCompileContext();
        ctx.entities.put("AppUser", user);
        ctx.entities.put("AppRole", role);
        ctx.entities.put("AppDept", dept);
        return ctx;
    }

    static ICompiledSql compile(String eql) {
        return new EqlCompiler().compile("test", eql, context);
    }

    static ICompiledSql compile(TestCompileContext ctx, String eql) {
        return new EqlCompiler().compile("test", eql, ctx);
    }

    static String sqlOf(String eql) {
        return compile(eql).getSql().getText().replaceAll("\\s+", " ");
    }

    static int countOccurrences(String text, String sub) {
        int count = 0;
        int idx = 0;
        while ((idx = text.indexOf(sub, idx)) >= 0) {
            count++;
            idx += sub.length();
        }
        return count;
    }

    static class TestCompileContext implements ISqlCompileContext {
        final Map<String, OrmEntityModel> entities = new HashMap<>();
        IDialect effectiveDialect;
        boolean enableFilter;

        TestCompileContext() {
            this.effectiveDialect = dialect;
        }

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
            return enableFilter;
        }

        @Override
        public IEqlAstTransformer getAstTransformer() {
            return null;
        }

        @Override
        public IDialect getDialectForQuerySpace(String querySpace) {
            return effectiveDialect;
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

    // ---------- 基础 SELECT 翻译 ----------

    @Test
    public void testSelectColumnQualifiesPhysicalNames() {
        // 实体属性名翻译为物理列名，FROM 子句使用物理表名
        String sql = sqlOf("select o.name from AppUser o where o.status = 'A'");
        assertTrue(sql.contains("from APP_USER"), sql);
        assertTrue(sql.contains("o.NAME"), sql);
        assertTrue(sql.contains("o.STATUS"), sql);
        // 字符串字面量内联渲染
        assertTrue(sql.contains("'A'"), sql);
    }

    @Test
    public void testSelectStatementMetadata() {
        ICompiledSql compiled = compile("select o.id, o.name from AppUser o");
        assertEquals(SqlStatementKind.SELECT, compiled.getStatementKind());
        assertEquals("spaceA", compiled.getQuerySpace());
        assertTrue(compiled.getReadEntityNames().contains("AppUser"), compiled.getReadEntityNames().toString());
        assertNull(compiled.getWriteEntityName());
        assertEquals(2, compiled.getDataSetMeta().getFieldCount());
    }

    @Test
    public void testSelectStarExpandsAllColumns() {
        ICompiledSql compiled = compile("select * from AppUser o");
        // AppUser 三个字段全部展开
        assertEquals(3, compiled.getDataSetMeta().getFieldCount());
        String sql = compiled.getSql().getText();
        assertTrue(sql.contains("SID"), sql);
        assertTrue(sql.contains("NAME"), sql);
        assertTrue(sql.contains("STATUS"), sql);
    }

    // ---------- 参数绑定 ----------

    @Test
    public void testWhereParamBindingOrder() {
        ICompiledSql compiled = compile("select o.name from AppUser o where o.status = ? and o.name = ?");
        String text = compiled.getSql().getText();
        assertEquals(2, countOccurrences(text, "?"), text);

        List<Object> params = compiled.buildParams(Arrays.asList("A", "n1"));
        assertEquals(Arrays.asList("A", "n1"), params);
    }

    @Test
    public void testUpdateParamBindingOrderFollowsSetBeforeWhere() {
        ICompiledSql compiled = compile("update AppUser o set o.name = ?, o.status = ? where o.id = ?");
        assertEquals(SqlStatementKind.UPDATE, compiled.getStatementKind());
        assertEquals("AppUser", compiled.getWriteEntityName());
        // set 子句参数先于 where 参数
        List<Object> params = compiled.buildParams(Arrays.asList("n", "S", "1"));
        assertEquals(Arrays.asList("n", "S", "1"), params);
    }

    @Test
    public void testInsertValuesTranslation() {
        ICompiledSql compiled = compile("insert into AppUser(id, name) values('1', 'tom')");
        assertEquals(SqlStatementKind.INSERT, compiled.getStatementKind());
        assertEquals("AppUser", compiled.getWriteEntityName());

        String sql = sqlOf("insert into AppUser(id, name) values('1', 'tom')");
        assertTrue(sql.contains("insert into APP_USER"), sql);
        assertTrue(sql.contains("SID"), sql);
        assertTrue(sql.contains("NAME"), sql);
        assertTrue(sql.contains("'1'"), sql);
        assertTrue(sql.contains("'tom'"), sql);
    }

    @Test
    public void testDeleteTranslationDropsAlias() {
        ICompiledSql compiled = compile("delete from AppUser o where o.id = '3'");
        assertEquals(SqlStatementKind.DELETE, compiled.getStatementKind());
        assertEquals("AppUser", compiled.getWriteEntityName());
        assertTrue(compiled.getReadEntityNames().contains("AppUser"), compiled.getReadEntityNames().toString());

        String sql = compiled.getSql().getText().replaceAll("\\s+", " ");
        assertTrue(sql.contains("delete from APP_USER"), sql);
        assertTrue(sql.contains("where SID = '3'"), sql);
    }

    // ---------- 分页 limit/offset ----------

    @Test
    public void testLimitOffsetTranslation() {
        // 缺省分页处理器为 LimitOffset：EQL LIMIT/OFFSET 原样落到 SQL
        String sql = sqlOf("select o.name from AppUser o order by o.id limit 10 offset 5");
        assertTrue(sql.contains("LIMIT"), sql);
        assertTrue(sql.contains("OFFSET"), sql);
        assertTrue(sql.contains("10"), sql);
        assertTrue(sql.contains("5"), sql);
    }

    @Test
    public void testLimitOnlyHasNoOffset() {
        String sql = sqlOf("select o.name from AppUser o limit 3");
        assertTrue(sql.contains("LIMIT"), sql);
        assertFalse(sql.toUpperCase().contains("OFFSET"), sql);
    }

    // ---------- 聚合 / group by / having / order by / distinct ----------

    @Test
    public void testCountAggregateTranslation() {
        ICompiledSql compiled = compile("select count(*) from AppUser o");
        String sql = compiled.getSql().getText();
        assertTrue(sql.toLowerCase().contains("count"), sql);
        assertEquals(1, compiled.getDataSetMeta().getFieldCount());
    }

    @Test
    public void testGroupByHavingTranslation() {
        String sql = sqlOf("select o.status, count(*) from AppUser o group by o.status having count(*) > 1");
        assertTrue(sql.toLowerCase().contains("group by"), sql);
        assertTrue(sql.toLowerCase().contains("having"), sql);
        assertTrue(sql.toLowerCase().contains("count"), sql);
        assertTrue(sql.contains("o.STATUS"), sql);
    }

    @Test
    public void testOrderByDirectionPreserved() {
        String sql = sqlOf("select o.name from AppUser o order by o.name desc");
        assertTrue(sql.contains("order by"), sql);
        assertTrue(sql.contains("desc"), sql);
    }

    @Test
    public void testDistinctTranslation() {
        String sql = sqlOf("select distinct o.status from AppUser o");
        assertTrue(sql.contains("distinct"), sql);
    }

    // ---------- 函数翻译 ----------

    @Test
    public void testNativeFunctionWithParenthesis() {
        String sql = sqlOf("select upper(o.name) from AppUser o");
        assertTrue(sql.contains("upper("), sql);
        assertTrue(sql.contains("o.NAME"), sql);
    }

    @Test
    public void testNativeFunctionWithoutParenthesis() {
        // sys_utc_now 注册为无括号关键字函数，调用写法带括号但输出不能带括号
        String sql = sqlOf("select sys_utc_now() from AppUser o");
        assertTrue(sql.contains("sys_utc_now"), sql);
        assertFalse(sql.contains("sys_utc_now("), sql);
    }

    @Test
    public void testUnknownFunctionFailsAtCompileTime() {
        // 方言未注册的函数必须在编译期报错，而不是生成非法 SQL
        NopException e = assertThrows(NopException.class,
                () -> compile("select reverse_str(o.name) from AppUser o"));
        assertEquals(ERR_EQL_UNKNOWN_FUNCTION.getErrorCode(), e.getErrorCode());
    }

    @Test
    public void testIlikeTranslatedToRegisteredFunction() {
        // 方言注册了 ilike 原生函数后，!~ 关键字应翻译为该函数调用
        String sql = sqlOf("select o.name from AppUser o where o.name ilike 'a%'");
        assertTrue(sql.contains("ilike("), sql);
    }

    // ---------- 条件表达式翻译 ----------

    @Test
    public void testIsNullAndIsNotNull() {
        String sql = sqlOf("select o.name from AppUser o where o.status is null");
        assertTrue(sql.contains("is null"), sql);

        sql = sqlOf("select o.name from AppUser o where o.status is not null");
        assertTrue(sql.contains("is not null"), sql);
    }

    @Test
    public void testInListTranslation() {
        String sql = sqlOf("select o.name from AppUser o where o.status in ('A', 'B')");
        assertTrue(sql.contains("in"), sql);
        assertTrue(sql.contains("'A'"), sql);
        assertTrue(sql.contains("'B'"), sql);
    }

    @Test
    public void testBetweenTranslation() {
        String sql = sqlOf("select o.name from AppUser o where o.id between '1' and '9'");
        assertTrue(sql.toLowerCase().contains("between"), sql);
        assertTrue(sql.contains("'1'"), sql);
        assertTrue(sql.contains("'9'"), sql);
    }

    @Test
    public void testLikeTranslation() {
        String sql = sqlOf("select o.name from AppUser o where o.name like 'a%'");
        assertTrue(sql.toLowerCase().contains("like"), sql);
    }

    @Test
    public void testAndOrParenthesization() {
        String sql = sqlOf(
                "select o.name from AppUser o where (o.status = 'A' or o.status = 'B') and o.name = 'x'");
        assertTrue(sql.contains("or"), sql);
        assertTrue(sql.contains("and"), sql);
        // 左括号必须保留，否则 and/or 优先级改变语义
        assertTrue(sql.contains("("), sql);
    }

    // ---------- join / 关系翻译 ----------

    @Test
    public void testToOneRelationNavigationGeneratesJoin() {
        // o.dept.name 跨 to-one 关系应生成到 APP_DEPT 的 join
        ICompiledSql compiled = compile(relationContext(), "select o.name, o.dept.name from AppUser o");
        String sql = compiled.getSql().getText().replaceAll("\\s+", " ");
        assertTrue(sql.contains("join"), sql);
        assertTrue(sql.contains("APP_DEPT"), sql);
        assertEquals(2, compiled.getDataSetMeta().getFieldCount());
        // 读取实体包含 join 到达的两个实体
        assertTrue(compiled.getReadEntityNames().contains("AppDept"), compiled.getReadEntityNames().toString());
    }

    @Test
    public void testExplicitJoinCondition() {
        String sql = sqlOf("select o.name from AppUser o join AppRole r on o.id = r.userId");
        assertTrue(sql.contains("join APP_ROLE"), sql);
        assertTrue(sql.contains("o.SID"), sql);
        assertTrue(sql.contains("r.USER_ID"), sql);
    }

    @Test
    public void testExistsSubQueryTranslation() {
        String sql = sqlOf("select o.name from AppUser o where exists (select 1 from AppRole r where r.userId = o.id)");
        assertTrue(sql.contains("exists"), sql);
        assertTrue(sql.contains("APP_ROLE"), sql);
    }

    @Test
    public void testScalarSubQueryInProjection() {
        ICompiledSql compiled = compile(relationContext(),
                "select o.name, (select count(*) from AppRole r where r.userId = o.id) as roleCount from AppUser o");
        String sql = compiled.getSql().getText().replaceAll("\\s+", " ");
        assertTrue(sql.contains("select count(*)"), sql);
        assertTrue(sql.contains("roleCount"), sql);
        assertTrue(sql.contains("APP_ROLE"), sql);
        assertEquals(2, compiled.getDataSetMeta().getFieldCount());
    }

    @Test
    public void testCollectionSomeOperatorExpandsSubQuery() {
        // to-many 集合的 _some 操作符翻译为相关 exists 子查询
        ICompiledSql compiled = compile(relationContext(),
                "select o.name from AppUser o where o.roles._some.name = 'admin'");
        String sql = compiled.getSql().getText().replaceAll("\\s+", " ");
        assertTrue(sql.contains("exists"), sql);
        assertTrue(sql.contains("APP_ROLE"), sql);
    }

    // ---------- union / case when / querySpace ----------

    @Test
    public void testUnionAllTranslation() {
        String sql = sqlOf("select o.id from AppUser o union all select r.id from AppRole r");
        assertTrue(sql.contains("union all"), sql);
        assertEquals(1, countOccurrences(sql, "APP_USER"));
        assertEquals(1, countOccurrences(sql, "APP_ROLE"));
    }

    @Test
    public void testCaseWhenTranslation() {
        String sql = sqlOf(
                "select case when o.status = 'A' then '1' else '0' end from AppUser o");
        String lower = sql.toLowerCase();
        assertTrue(lower.contains("case"), sql);
        assertTrue(lower.contains("when"), sql);
        assertTrue(lower.contains("else"), sql);
        assertTrue(lower.contains("end"), sql);
    }

    @Test
    public void testQuerySpaceFollowsEntityQuerySpaceInContext() {
        // 编译上下文把实体的querySpace改为spaceB时，语句querySpace随之解析为spaceB
        TestCompileContext ctx = baseContext();
        ctx.entities.put("AppUser", entity("AppUser", "APP_USER", "spaceB",
                col("id", "SID", 1, true), col("name", "NAME", 2, false),
                col("status", "STATUS", 3, false)));

        ICompiledSql compiled = compile(ctx, "select o.name from AppUser o");
        assertEquals("spaceB", compiled.getQuerySpace());
    }

    @Test
    public void testMultipleStatementsRejected() {
        assertThrows(NopException.class,
                () -> compile("select o.name from AppUser o; select o.name from AppUser o"));
    }

    // ---------- no-from / dual ----------

    @Test
    public void testNoFromSelectUsesDual() {
        // 无 from 的 select 经 selectFromDual 模板包装为 from dual 查询
        ICompiledSql compiled = compile("select 'xyz'");
        assertEquals(1, compiled.getDataSetMeta().getFieldCount());
        String sql = compiled.getSql().getText();
        assertTrue(sql.contains("'xyz'"), sql);
        assertTrue(sql.contains("from dual"), sql);
        assertFalse(sql.contains("APP_USER"), sql);
    }
}
