package io.nop.orm.eql.compile;

import io.nop.api.core.exceptions.NopException;
import io.nop.commons.type.StdSqlType;
import io.nop.dao.dialect.IDialect;
import io.nop.dao.dialect.impl.DialectImpl;
import io.nop.dao.dialect.model.DialectFeatures;
import io.nop.dao.dialect.model.DialectModel;
import io.nop.dao.dialect.model.SqlNativeFunctionModel;
import io.nop.orm.eql.ICompiledSql;
import io.nop.orm.eql.IEqlAstTransformer;
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
import java.util.Map;

import static io.nop.orm.eql.OrmEqlErrors.ERR_EQL_NOT_ALLOW_MULTIPLE_QUERY_SPACE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 基于最小实体模型的端到端编译测试：EqlCompiler -> EqlTransformVisitor -> AstToSqlGenerator
 */
public class TestEqlCompileSql {
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
        model.setSqls(new io.nop.dao.dialect.model.DialectSqls());
        model.setSqlDataTypes(Collections.emptyList());

        SqlNativeFunctionModel currentTimestamp = new SqlNativeFunctionModel();
        currentTimestamp.setName("current_timestamp");
        currentTimestamp.setHasParenthesis(false);
        currentTimestamp.setReturnType(StdSqlType.TIMESTAMP);

        SqlNativeFunctionModel upper = new SqlNativeFunctionModel();
        upper.setName("upper");
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
        boolean allowUnderscoreName;

        @Override
        public boolean isDisableLogicalDelete() {
            return false;
        }

        @Override
        public boolean isAllowUnderscoreName() {
            return allowUnderscoreName;
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

    @Test
    public void testQuerySpaceNotResetByNoFromSubQuery() {
        // AppUser位于spaceA，投影中的无from子查询不能把整体querySpace重置回默认空间
        ICompiledSql compiled = compile("select o.name from AppUser o");
        assertEquals("spaceA", compiled.getQuerySpace());

        compiled = compile("select o.name, (select 1) from AppUser o");
        assertEquals("spaceA", compiled.getQuerySpace());
    }

    @Test
    public void testNoFromSubQueryWithConflictingQuerySpaceThrows() {
        // 子查询显式指定不同的querySpace应报错，而不是静默覆盖
        NopException e = assertThrows(NopException.class,
                () -> compile("select o.name, (@querySpace('spaceB') select 1) from AppUser o"));
        assertEquals(ERR_EQL_NOT_ALLOW_MULTIPLE_QUERY_SPACE.getErrorCode(), e.getErrorCode());
    }

    @Test
    public void testSelectAllWithExplicitJoin() {
        // select * 搭配显式join时应展开左右两表的全部列，而不是抛出IllegalStateException
        ICompiledSql compiled = compile("select * from AppUser o join AppRole r on o.id = r.userId");
        String sql = compiled.getSql().getText();
        System.out.println(sql);

        assertEquals(4, compiled.getDataSetMeta().getFieldCount());
        assertTrue(sql.contains("join"), sql);
    }

    @Test
    public void testUpdateReturningNonColumnExpr() {
        // returning投影为函数表达式时应正常编译，不能在SQL生成阶段NPE
        ICompiledSql compiled = compile("update AppUser o set o.name = 'x' where o.id = '1' returning upper(o.name)");
        String sql = compiled.getSql().getText();
        System.out.println(sql);
        assertTrue(sql.contains("returning"), sql);
    }

    @Test
    public void testDeleteCorrelatedSubQueryQualifiesTargetTable() {
        // 相关子查询内引用UPDATE/DELETE目标表别名时必须用物理表名限定，
        // 不能按别名丢弃前缀导致绑定到子查询表(APP_ROLE)的同名列SID
        ICompiledSql compiled = compile(
                "delete from AppUser o where o.id in (select r.userId from AppRole r where r.userId = o.id)");
        String sql = compiled.getSql().getText().replaceAll("\\s+", " ");
        System.out.println(sql);

        String qualifier = dialect.normalizeTableName("APP_USER");
        // 顶层引用丢弃前缀
        assertTrue(sql.contains("where SID in"), sql);
        // 相关子查询内改用物理表名限定
        assertTrue(sql.contains(qualifier + ".SID"), sql);
    }

    @Test
    public void testDeleteSubQueryShadowAliasKeepsPrefix() {
        // 子查询中定义了与目标表相同的别名o时，子查询内o.xxx引用的是子查询表，前缀必须保留
        ICompiledSql compiled = compile(
                "delete from AppUser o where o.id in (select o.userId from AppRole o where o.userId = o.id)");
        String sql = compiled.getSql().getText();
        System.out.println(sql);

        assertTrue(sql.contains("o.USER_ID"), sql);
        assertTrue(sql.contains("o.SID"), sql);
    }

    @Test
    public void testUpdateTopLevelColumnsDropAlias() {
        ICompiledSql compiled = compile("update AppUser o set o.name = 'x' where o.id = '1'");
        String sql = compiled.getSql().getText().replaceAll("\\s+", " ");
        System.out.println(sql);

        assertTrue(sql.contains("set NAME"), sql);
        assertTrue(sql.contains("where SID"), sql);
    }

    @Test
    public void testSubQueryFieldByUnderscoreName() {
        // 与实体字段一致，子查询结果字段允许通过下划线形式访问
        TestCompileContext ctx = new TestCompileContext();
        ctx.entities.putAll(context.entities);
        ctx.allowUnderscoreName = true;

        ICompiledSql compiled = new EqlCompiler().compile("test",
                "select t.user_id from (select o.id as userId, o.name as userName from AppUser o) t", ctx);
        String sql = compiled.getSql().getText();
        System.out.println(sql);
        assertTrue(sql.contains("userId"), sql);
    }
}
