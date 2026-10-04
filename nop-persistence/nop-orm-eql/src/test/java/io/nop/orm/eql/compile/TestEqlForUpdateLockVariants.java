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
 * WI12 交付②：`FOR UPDATE NOWAIT` / `FOR UPDATE SKIP LOCKED` 语法（plan 38 Phase 2）。
 *
 * <p>文法面（裁定 = ai-dev/design/eql-cross-db-compat/2026-10-05-wi12-phase0-adjudications.md §1）：
 * 变体区分上移 `sqlQuerySelect` 层分离标签——`forUpdate=forUpdate_ (nowait=NOWAIT |
 * skipLocked=skipLocked_)?`，AST boolean 字段（nowait 复用既有 / skipLocked 新增）经
 * 生成的 prop 直达赋值消费（`SqlQuerySelect_selectAll`/`_forUpdate` 双先例）。
 * NOWAIT/SKIP/LOCKED 三 token 追加（Literals.g4 尾部、零删除）+ unreservedWord_ 双登记
 * （标识符位置可解析，WI7b rollup/cube 先例）。
 *
 * <p>生成器面：printForUpdate 按字段取 `<forUpdateNowait>`/`<forUpdateSkipLocked>` 配置文本；
 * 未配置方言编译期响亮（feature=forUpdateNowait / forUpdateSkipLocked）；解析层文法单选备选
 * 使 NOWAIT 与 SKIP LOCKED 天然互斥（`for update nowait skip locked` 解析响亮失败）。
 * 回显路径保持硬编码（WI1 裁定 BC）。
 */
public class TestEqlForUpdateLockVariants extends BaseTestCase {
    static final String PLAIN_EQL = "select o.id from AppUser o where o.name = 'a' for update";
    static final String NOWAIT_EQL = PLAIN_EQL + " nowait";
    static final String SKIP_LOCKED_EQL = PLAIN_EQL + " skip locked";

    static IDialect fullDialect;
    static IDialect noVariantDialect;

    @BeforeAll
    public static void init() {
        fullDialect = buildDialect(" for update ", " for update nowait ", " for update skip locked ");
        noVariantDialect = buildDialect(" for update ", null, null);
    }

    static DialectModel newDialectModel(String forUpdate, String forUpdateNowait, String forUpdateSkipLocked) {
        DialectModel model = new DialectModel();
        model.setReservedKeywords(Collections.emptySet());
        io.nop.dao.dialect.model.DialectSqls sqls = new io.nop.dao.dialect.model.DialectSqls();
        sqls.setForUpdate(forUpdate);
        sqls.setForUpdateNowait(forUpdateNowait);
        sqls.setForUpdateSkipLocked(forUpdateSkipLocked);
        model.setSqls(sqls);
        model.setSqlDataTypes(Collections.emptyList());
        model.setFeatures(new io.nop.dao.dialect.model.DialectFeatures());
        return model;
    }

    static IDialect buildDialect(String forUpdate, String forUpdateNowait, String forUpdateSkipLocked) {
        DialectModel model = newDialectModel(forUpdate, forUpdateNowait, forUpdateSkipLocked);
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

    static TestEqlCompileSql.TestCompileContext baseContext(IDialect dialect) {
        TestEqlCompileSql.TestCompileContext ctx = new TestEqlCompileSql.TestCompileContext();
        ctx.effectiveDialect = dialect;
        ctx.entities.put("AppUser", TestEqlCompileSql.entity("AppUser", "APP_USER", "spaceA",
                TestEqlCompileSql.col("id", "SID", 1, true),
                TestEqlCompileSql.col("name", "NAME", 2, false)));
        return ctx;
    }

    static String sqlOf(IDialect dialect, String eql) {
        ICompiledSql compiled = new EqlCompiler().compile("test", eql, baseContext(dialect));
        return compiled.getSql().getText().replaceAll("\\s+", " ").trim();
    }

    @Test
    public void testForUpdateNowaitCompiledText() {
        // 正向（roadmap EC ①）：解析 + lowering 与方言配置值一致
        String sql = sqlOf(fullDialect, NOWAIT_EQL);
        assertTrue(sql.endsWith("for update nowait"), sql);
    }

    @Test
    public void testForUpdateSkipLockedCompiledText() {
        String sql = sqlOf(fullDialect, SKIP_LOCKED_EQL);
        assertTrue(sql.endsWith("for update skip locked"), sql);
    }

    @Test
    public void testPlainForUpdateUnaffected() {
        // 既有形态零漂移：裸 for update 仍走 <forUpdate> 模板
        String sql = sqlOf(fullDialect, PLAIN_EQL);
        assertTrue(sql.endsWith("for update"), sql);
    }

    @Test
    public void testNowaitUnsupportedDialectFailsLoud() {
        // 负向（roadmap EC ②）：未配置 <forUpdateNowait> 的方言编译期响亮，不回退裸 for update
        NopException e = assertThrows(NopException.class, () -> sqlOf(noVariantDialect, NOWAIT_EQL));
        assertEquals(ERR_EQL_DIALECT_NOT_SUPPORT_FEATURE.getErrorCode(), e.getErrorCode());
        assertEquals("forUpdateNowait", e.getParam("feature"));
    }

    @Test
    public void testSkipLockedUnsupportedDialectFailsLoud() {
        // 负向：nowait 与 skipLocked 各自独立触发（不能只测组合）
        NopException e = assertThrows(NopException.class, () -> sqlOf(noVariantDialect, SKIP_LOCKED_EQL));
        assertEquals(ERR_EQL_DIALECT_NOT_SUPPORT_FEATURE.getErrorCode(), e.getErrorCode());
        assertEquals("forUpdateSkipLocked", e.getParam("feature"));
    }

    @Test
    public void testNowaitAndSkipLockedMutuallyExclusiveAtParse() {
        // 文法单选备选天然互斥：`for update nowait skip locked` 解析响亮失败
        assertThrows(NopException.class,
                () -> new EqlASTParser().parseFromText(null, PLAIN_EQL + " nowait skip locked"));
    }

    @Test
    public void testLockWordsAsIdentifiers() {
        // unreservedWord_ 双登记守护：nowait/skip/locked 作列名/别名仍可解析（WI7b rollup/cube 先例）
        SqlProgram program = new EqlASTParser().parseFromText(null,
                "select nowait, skip, locked from T t order by locked");
        String echo = program.toSqlString().replaceAll("\\s+", " ");
        assertTrue(echo.contains("nowait") && echo.contains("skip") && echo.contains("locked"), echo);
    }

    @Test
    public void testEchoPathStaysHardcoded() {
        // 回显路径（AstToEqlGenerator）保持 WI1 裁定硬编码：锁变体后缀不回显（BC 判据）
        SqlProgram program = new EqlASTParser().parseFromText(null, NOWAIT_EQL);
        String echo = program.toSqlString().replaceAll("\\s+", " ").trim();
        assertTrue(echo.endsWith("for update"), echo);
    }
}
