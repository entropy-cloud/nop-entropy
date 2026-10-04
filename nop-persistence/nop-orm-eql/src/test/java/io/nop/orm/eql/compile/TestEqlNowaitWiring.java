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
import io.nop.orm.eql.ast.SqlQuerySelect;
import io.nop.orm.eql.ast.SqlStatement;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WI12 交付①：{@code SqlQuerySelect.nowait} 的生成器接线（roadmap WI12 EC ② 的独立正向用例面）。
 *
 * <p>缺陷（修复前）：AST 字段 {@code nowait} 自生成以来手写侧零消费、文法侧零 population——
 * {@code AstToSqlGenerator.printForUpdate} 无视 nowait，恒输出 {@code <forUpdate>} 裸锁文本
 * （WI1 BUG-3 修复只接了 boolean forUpdate 的方言模板）。
 *
 * <p>修复后（裁定 = ai-dev/design/eql-cross-db-compat/2026-10-05-wi12-phase0-adjudications.md §1）：
 * nowait=true 时锁文本取 {@code <sqls>} 的 {@code <forUpdateNowait>} 配置（presence = 能力 + 文本
 * 二合一）；未配置方言编译期响亮 {@code ERR_EQL_DIALECT_NOT_SUPPORT_FEATURE}
 * （feature=forUpdateNowait）——不允许静默降级为裸 {@code for update}（"只打印不设门 = 净回归"）。
 * 程序化矛盾输入（forUpdate=false && nowait=true）生成器入口 IllegalArgumentException 响亮
 * （解析层到不了该组合，仅程序化构造可达）。
 *
 * <p>AST 级 nowait 赋值经 {@link IEqlAstTransformer#transformBeforeAnalyze} 钩子进入真实
 * EqlCompiler 管线（parse → AST mutate → transform → generate 三层连通，Minimum Rules #23）。
 * 文法侧 population（{@code for update nowait} 可解析）归 Phase 2。
 */
public class TestEqlNowaitWiring extends BaseTestCase {
    static final String EQL = "select o.id from AppUser o where o.name = 'a' for update";

    static IDialect nowaitDialect;
    static IDialect bareDialect;

    @BeforeAll
    public static void init() {
        nowaitDialect = buildDialect(" for update ", " for update nowait ");
        bareDialect = buildDialect(" for update ", null);
    }

    static DialectModel newDialectModel(String forUpdate, String forUpdateNowait) {
        DialectModel model = new DialectModel();
        model.setReservedKeywords(Collections.emptySet());
        io.nop.dao.dialect.model.DialectSqls sqls = new io.nop.dao.dialect.model.DialectSqls();
        sqls.setForUpdate(forUpdate);
        sqls.setForUpdateNowait(forUpdateNowait);
        model.setSqls(sqls);
        model.setSqlDataTypes(Collections.emptyList());
        model.setFeatures(new io.nop.dao.dialect.model.DialectFeatures());
        return model;
    }

    static IDialect buildDialect(String forUpdate, String forUpdateNowait) {
        DialectModel model = newDialectModel(forUpdate, forUpdateNowait);
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

    static class NowaitCompileContext implements ISqlCompileContext {
        final Map<String, OrmEntityModel> entities = new HashMap<>();
        IDialect effectiveDialect;
        boolean forUpdate = true;
        boolean nowait;
        IEqlAstTransformer delegate;

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
            return new IEqlAstTransformer() {
                @Override
                public void transformBeforeAnalyze(SqlProgram ast, String name, String sql,
                                                   ISqlCompileContext context) {
                    if (delegate != null)
                        delegate.transformBeforeAnalyze(ast, name, sql, context);
                    SqlStatement stm = ast.getStatements().get(0);
                    if (stm instanceof SqlQuerySelect) {
                        SqlQuerySelect select = (SqlQuerySelect) stm;
                        select.setForUpdate(forUpdate);
                        select.setNowait(nowait);
                    }
                }

                @Override
                public void transformAfterAnalyze(SqlProgram ast, String name, String sql,
                                                  ISqlCompileContext context) {
                    if (delegate != null)
                        delegate.transformAfterAnalyze(ast, name, sql, context);
                }
            };
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

    static NowaitCompileContext baseContext(IDialect dialect, boolean nowait) {
        NowaitCompileContext ctx = new NowaitCompileContext();
        ctx.effectiveDialect = dialect;
        ctx.nowait = nowait;
        ctx.entities.put("AppUser", entity("AppUser", "APP_USER",
                col("id", "SID", 1, true), col("name", "NAME", 2, false)));
        return ctx;
    }

    static String sqlOf(IDialect dialect, boolean nowait) {
        ICompiledSql compiled = new EqlCompiler().compile("test", EQL, baseContext(dialect, nowait));
        return compiled.getSql().getText().replaceAll("\\s+", " ").trim();
    }

    @Test
    public void testNowaitConsumedFromDialectTemplate() {
        // 独立正向用例（roadmap WI12 EC ②）：nowait=true 的输出 ≠ nowait=false 的输出，
        // 且与方言配置值一致——证明生成器真的消费了 nowait 字段与配置模板
        // （WI1 testConfiguredTemplateIsConsumed 的自定义模板先例同款防"恰好同形"设计）
        String withNowait = sqlOf(nowaitDialect, true);
        assertTrue(withNowait.endsWith("for update nowait"), withNowait);

        String withoutNowait = sqlOf(nowaitDialect, false);
        assertTrue(withoutNowait.endsWith("for update"), withoutNowait);
        assertFalse(withoutNowait.contains("nowait"), withoutNowait);

        // nowait=true 与裸路径输出必须不同（接线差异证明）
        assertFalse(withNowait.equals(withoutNowait));
    }

    @Test
    public void testNowaitUnsupportedDialectFailsLoud() {
        // 负向用例（roadmap WI12 EC ②）：未配置 <forUpdateNowait> 的方言 + nowait=true
        // 编译期响亮——不允许静默降级为裸 for update（"只打印不设门 = 净回归"）
        NopException e = assertThrows(NopException.class, () -> sqlOf(bareDialect, true));
        assertEquals(ERR_EQL_DIALECT_NOT_SUPPORT_FEATURE.getErrorCode(), e.getErrorCode());
        assertEquals("forUpdateNowait", e.getParam("feature"));

        // 同方言 nowait=false 不受影响（裸 for update 路径保持 WI1 交付行为）
        String sql = sqlOf(bareDialect, false);
        assertTrue(sql.endsWith("for update"), sql);
    }

    @Test
    public void testContradictoryNowaitWithoutForUpdateFailsLoud() {
        // 矛盾输入响亮（裁定 §1.5）：forUpdate=false && nowait=true 仅程序化构造可达，
        // 生成器入口 IllegalArgumentException——不允许静默输出（或静默忽略）
        NowaitCompileContext ctx = baseContext(bareDialect, true);
        ctx.forUpdate = false;
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> new EqlCompiler().compile("test", EQL, ctx));
        assertTrue(e.getMessage().contains("nowait"), e.getMessage());
    }

    @Test
    public void testEchoPathStaysHardcoded() {
        // 回显路径（AstToEqlGenerator）按 WI1 裁定保持硬编码：nowait=true 的程序化 AST
        // 回显仍为 "for update"（BC 判据；文法 population 后的回显治理归后继裁定）
        SqlProgram program = new EqlASTParser().parseFromText(null, EQL);
        SqlStatement stm = program.getStatements().get(0);
        ((SqlQuerySelect) stm).setNowait(true);
        String echo = program.toSqlString().replaceAll("\\s+", " ").trim();
        assertTrue(echo.endsWith("for update"), echo);
        assertFalse(echo.contains("nowait"), echo);
    }
}
