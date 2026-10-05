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
import io.nop.core.unittest.BaseTestCase;
import io.nop.dao.dialect.IDialect;
import io.nop.dao.dialect.impl.DialectImpl;
import io.nop.dao.dialect.model.DialectModel;
import io.nop.dao.dialect.model.SqlNativeFunctionModel;
import io.nop.orm.eql.ICompiledSql;
import io.nop.orm.eql.IEqlAstTransformer;
import io.nop.orm.eql.ast.SqlProgram;
import io.nop.orm.eql.meta.EntityTableMeta;
import io.nop.orm.eql.meta.ISqlTableMeta;
import io.nop.orm.eql.parse.EqlASTParser;
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

import static io.nop.orm.eql.OrmEqlErrors.ERR_EQL_DIALECT_NOT_SUPPORT_FEATURE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WI1 BUG-3：`for update` 硬编码绕过方言锁模板。
 *
 * <p>缺陷（修复前）：`AstToEqlGenerator.printSelect` 硬编码 {@code print("for update")}，
 * `AstToSqlGenerator` 无 override —— EQL 路径完全绕过 `IDialect.getForUpdateSql`，
 * 在锁模板为空的方言（如 duckdb，`<forUpdate x:override="replace"/>`）上静默产出
 * 数据库不支持的 `for update` 文本。
 *
 * <p>修复后（裁定 = WI1 裁决文档；plan Phase 3）：执行路径（`AstToSqlGenerator`）走
 * `dialect.getForUpdateSql(null)`——模板非空时输出方言锁文本，空模板时编译期报错
 * （复用 `ERR_EQL_DIALECT_NOT_SUPPORT_FEATURE`）；回显路径（基类）硬编码保持不动
 * （WI0 语料基线只比对 `toSqlString()` 回显，7 个语料文件均不含 `for update`）。
 * AST 只有 boolean `forUpdate`、无 LockOption，null 调用安全（NOWAIT/SKIP LOCKED 归 WI12）。
 */
public class TestEqlForUpdateDialectGate extends BaseTestCase {
    static final String EQL = "select o.id from AppUser o where o.name = 'a' for update";

    static IDialect emptyTemplateDialect;
    static IDialect customTemplateDialect;

    @BeforeAll
    public static void init() {
        emptyTemplateDialect = buildDialect(null);
        customTemplateDialect = buildDialect(" for update nowait ");
    }

    static DialectModel newDialectModel(String forUpdate) {
        DialectModel model = new DialectModel();
        model.setReservedKeywords(Collections.emptySet());
        io.nop.dao.dialect.model.DialectSqls sqls = new io.nop.dao.dialect.model.DialectSqls();
        // duckdb 同形：显式空模板（<forUpdate x:override="replace"/> 加载后为 null）
        sqls.setForUpdate(forUpdate);
        model.setSqls(sqls);
        model.setSqlDataTypes(Collections.emptyList());
        model.setFeatures(new io.nop.dao.dialect.model.DialectFeatures());
        return model;
    }

    static IDialect buildDialect(String forUpdate) {
        DialectModel model = newDialectModel(forUpdate);
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

    static TestCompileContext baseContext(IDialect dialect) {
        TestCompileContext ctx = new TestCompileContext();
        ctx.effectiveDialect = dialect;
        ctx.entities.put("AppUser", entity("AppUser", "APP_USER",
                col("id", "SID", 1, true), col("name", "NAME", 2, false)));
        return ctx;
    }

    static String sqlOf(IDialect dialect, String eql) {
        ICompiledSql compiled = new EqlCompiler().compile("test", eql, baseContext(dialect));
        return compiled.getSql().getText().replaceAll("\\s+", " ");
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

    @Test
    public void testEmptyForUpdateTemplateThrowsAtCompileTime() {
        // 修复前红：空模板方言上静默输出 "for update"（duckdb 不支持该语法）
        NopException e = assertThrows(NopException.class, () -> sqlOf(emptyTemplateDialect, EQL));
        assertEquals(ERR_EQL_DIALECT_NOT_SUPPORT_FEATURE.getErrorCode(), e.getErrorCode());
        assertEquals("forUpdate", e.getParam("feature"));
    }

    @Test
    public void testConfiguredTemplateIsConsumed() {
        // 正向用例：输出文本与方言 getForUpdateSql 返回值一致——
        // 刻意用与硬编码文本不同的模板值证明模板真的被消费（而非恰好同形）
        String sql = sqlOf(customTemplateDialect, EQL);
        assertTrue(sql.trim().endsWith("for update nowait"), sql);
    }

    @Test
    public void testEchoPathStaysHardcoded() {
        // 回显路径（AstToEqlGenerator）不受方言模板影响，保持硬编码 "for update"
        SqlProgram program = new EqlASTParser().parseFromText(null, EQL);
        String echo = program.toSqlString().replaceAll("\\s+", " ");
        assertTrue(echo.endsWith("for update"), echo);
    }
}
