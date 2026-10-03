/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.eql.compile;

import io.nop.api.core.config.AppConfig;
import io.nop.api.core.exceptions.NopException;
import io.nop.commons.type.StdDataType;
import io.nop.commons.type.StdSqlType;
import io.nop.core.CoreConfigs;
import io.nop.dao.dialect.IDialect;
import io.nop.dao.dialect.impl.DialectImpl;
import io.nop.dao.dialect.model.DialectFeatures;
import io.nop.dao.dialect.model.DialectModel;
import io.nop.dao.dialect.model.SqlNativeFunctionModel;
import io.nop.orm.eql.ICompiledSql;
import io.nop.orm.eql.IEqlAstTransformer;
import io.nop.orm.eql.ast.SqlExpr;
import io.nop.orm.eql.ast.SqlInValuesExpr;
import io.nop.orm.eql.ast.SqlNumberLiteral;
import io.nop.orm.eql.meta.EntityTableMeta;
import io.nop.orm.eql.meta.ISqlTableMeta;
import io.nop.orm.eql.sql.IAliasGenerator;
import io.nop.orm.eql.sql.SeqAliasGenerator;
import io.nop.orm.model.OrmColumnModel;
import io.nop.orm.model.OrmEntityModel;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static io.nop.orm.eql.OrmEqlErrors.ERR_EQL_IN_LIST_TOO_LARGE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WI10b（WI10a 设计定稿 2026-10-03）：IN 列表长度门禁——EQL 编译路径五条契约测试。
 *
 * <p>策略契约（`ai-dev/design/eql-cross-db-compat/2026-10-03-wi10a-in-list-length-policy.md`）：
 * 统一阈值 1000（唯一硬依据 Oracle ORA-01795）、经 `CoreConfigs.CFG_CORE_SQL_IN_LIST_MAX_SIZE`
 * 可调高、无关闭取值；超限在编译管线（`EqlTransformVisitor#visitSqlInValuesExpr`）显式报错，
 * 门禁方言无关、不做改写；`NOT IN` 与 `IN` 同为 `SqlInValuesExpr` 节点同受本门；
 * 回显路径（`EqlASTNode#toSqlString`）不设门，保持忠实转储。
 *
 * <p>方言为最小手工 DialectModel（`TestEqlTranslationSemantics#buildDialect` 同一先例：
 * 模块测试环境没有 dialect.xml 组件加载器）。
 */
public class TestEqlInListLengthGate {
    static final int THRESHOLD = 1000;
    static IDialect dialect;

    @BeforeAll
    public static void init() {
        dialect = buildDialect();
    }

    @AfterAll
    public static void restoreConfig() {
        // 配置项是全局单例，测试后恢复默认，避免污染同模块其他测试
        AppConfig.getConfigProvider().updateConfigValue(CoreConfigs.CFG_CORE_SQL_IN_LIST_MAX_SIZE, THRESHOLD);
    }

    static IDialect buildDialect() {
        DialectModel model = new DialectModel();
        model.setReservedKeywords(Collections.emptySet());
        io.nop.dao.dialect.model.DialectSqls sqls = new io.nop.dao.dialect.model.DialectSqls();
        model.setSqls(sqls);
        model.setSqlDataTypes(Collections.emptyList());
        model.setFeatures(new DialectFeatures());
        SqlNativeFunctionModel currentTimestamp = new SqlNativeFunctionModel();
        currentTimestamp.setName("current_timestamp");
        currentTimestamp.setHasParenthesis(false);
        model.setFunctions(Collections.singletonList(currentTimestamp));
        return new DialectImpl(model);
    }

    static OrmColumnModel col(String name, String code, int propId, boolean primary) {
        OrmColumnModel c = new OrmColumnModel();
        c.setName(name);
        c.setCode(code);
        c.setPropId(propId);
        c.setStdSqlType(StdSqlType.VARCHAR);
        c.setStdDataType(StdDataType.STRING);
        c.setPrimary(primary);
        return c;
    }

    static OrmEntityModel entity(String name, String tableName, OrmColumnModel... cols) {
        OrmEntityModel m = new OrmEntityModel();
        m.setName(name);
        m.setTableName(tableName);
        m.setQuerySpace("spaceA");
        m.setColumns(new ArrayList<>(Arrays.asList(cols)));
        m.init();
        return m;
    }

    static TestCompileContext baseContext() {
        TestCompileContext ctx = new TestCompileContext();
        ctx.effectiveDialect = dialect;
        ctx.entities.put("AppDept", entity("AppDept", "APP_DEPT",
                col("id", "SID", 1, true), col("name", "NAME", 2, false)));
        return ctx;
    }

    static String inListSql(int count, boolean notIn) {
        StringBuilder sb = new StringBuilder("select o.id from AppDept o where o.id ");
        if (notIn)
            sb.append("not ");
        sb.append("in (");
        for (int i = 0; i < count; i++) {
            if (i > 0)
                sb.append(',');
            sb.append(i + 1);
        }
        sb.append(')');
        return sb.toString();
    }

    static ICompiledSql compile(String eql) {
        return new EqlCompiler().compile("testStmt", eql, baseContext());
    }

    static String sqlOf(String eql) {
        return compile(eql).getSql().getText().replaceAll("\\s+", " ");
    }

    static List<SqlExpr> numberLiterals(int count) {
        List<SqlExpr> values = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            SqlNumberLiteral literal = new SqlNumberLiteral();
            literal.setValue(String.valueOf(i + 1));
            values.add(literal);
        }
        return values;
    }

    /**
     * 测试一（阈值内正常）：999 个元素的 IN 与 NOT IN 均正常编译、SQL 文本正确。
     */
    @Test
    public void testInListWithinThresholdCompiles() {
        String inSql = sqlOf(inListSql(THRESHOLD - 1, false));
        assertTrue(inSql.contains("in ( 1 , 2"), "IN list expanded: " + inSql.substring(0, 60));
        String flat = inSql.replaceAll("\\s+", "");
        assertTrue(flat.endsWith("997,998,999)"), "all elements present: " + flat.substring(flat.length() - 20));

        String notInSql = sqlOf(inListSql(THRESHOLD - 1, true));
        assertTrue(notInSql.contains("not in"), "NOT IN compiled: " + notInSql.substring(0, 60));
    }

    /**
     * 测试二（超阈值报错）：1001 个元素编译期抛新错误码，断言具体错误码与
     * itemCount/maxItems/stmtName param 值（非泛化异常）。
     */
    @Test
    public void testInListOverThresholdThrowsWithCodeAndParams() {
        NopException e = assertThrows(NopException.class, () -> compile(inListSql(THRESHOLD + 1, false)));
        assertEquals(ERR_EQL_IN_LIST_TOO_LARGE.getErrorCode(), e.getErrorCode());
        assertEquals(String.valueOf(THRESHOLD + 1), String.valueOf(e.getParam("itemCount")));
        assertEquals(String.valueOf(THRESHOLD), String.valueOf(e.getParam("maxItems")));
        assertEquals("testStmt", e.getParam("stmtName"));
        // 门禁方言无关：dialect param 仅作定位信息，不因方言不同改变行为
        // （手构 DialectModel 无名称，两侧同为 null 也算一致——比较走 String.valueOf）
        assertEquals(String.valueOf(dialect.getName()), String.valueOf(e.getParam("dialect")));
    }

    /**
     * 测试三（NOT IN 同阈值）：`not in` 与 `in` 同为 SqlInValuesExpr 节点，超阈值同错误码，无单独放宽。
     */
    @Test
    public void testNotInOverThresholdSameError() {
        NopException e = assertThrows(NopException.class, () -> compile(inListSql(THRESHOLD + 1, true)));
        assertEquals(ERR_EQL_IN_LIST_TOO_LARGE.getErrorCode(), e.getErrorCode());
        assertEquals(String.valueOf(THRESHOLD + 1), String.valueOf(e.getParam("itemCount")));
    }

    /**
     * 测试四（回显不变）：超阈值的 SqlInValuesExpr 经 EqlASTNode#toSqlString() 仍完整转出
     * （门禁不在打印器/回显路径——WI10a 设计 §3.4：回显忠实转储 + 报错可诊断性依赖 dump 能力）。
     */
    @Test
    public void testEchoToSqlStringUnchangedOverThreshold() {
        SqlInValuesExpr expr = new SqlInValuesExpr();
        // visitSqlInValuesExpr 回显会先打印左操作数（IN 左侧表达式）
        SqlNumberLiteral lhs = new SqlNumberLiteral();
        lhs.setValue("1");
        expr.setExpr(lhs);
        expr.setValues(numberLiterals(THRESHOLD + 1));
        String text = expr.toSqlString();
        assertFalse(text.isEmpty(), "echo must stay available for diagnosis");
        // 回显含 pretty 缩进，比对时去除空白：1001 个元素以 1000 个逗号分隔全部转出——零截断
        String flat = text.replaceAll("\\s+", "");
        long commas = flat.chars().filter(c -> c == ',').count();
        assertEquals(THRESHOLD, commas, "all elements echoed (1000 separators for 1001 items)");
        assertTrue(flat.endsWith("1001)"), "last element intact: " + flat.substring(flat.length() - 10));
    }

    /**
     * 测试五之前置（配置通路接线验证，Minimum Rules #23）：调高配置后原超阈值查询可过
     * ——证明配置项被编译路径真实读取，不是只读常量。
     */
    @Test
    public void testConfigRaiseAllowsOverThresholdList() {
        try {
            AppConfig.getConfigProvider().updateConfigValue(CoreConfigs.CFG_CORE_SQL_IN_LIST_MAX_SIZE, THRESHOLD + 500);
            String sql = sqlOf(inListSql(THRESHOLD + 1, false));
            assertTrue(sql.replaceAll("\\s+", "").endsWith("1000,1001)"),
                    "over-threshold list now compiles: " + sql.substring(sql.length() - 40));
        } finally {
            AppConfig.getConfigProvider().updateConfigValue(CoreConfigs.CFG_CORE_SQL_IN_LIST_MAX_SIZE, THRESHOLD);
        }
        // 恢复默认后同查询重新被拒（证明不是一次性缓存放行）
        assertThrows(NopException.class, () -> compile(inListSql(THRESHOLD + 1, false)));
    }

    static class TestCompileContext implements ISqlCompileContext {
        final Map<String, OrmEntityModel> entities = new HashMap<>();
        IDialect effectiveDialect;
        boolean enableFilter;

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
}
