/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.eql.compile;

import io.nop.core.initialize.CoreInitialization;
import io.nop.dao.dialect.DialectManager;
import io.nop.dao.dialect.IDialect;
import io.nop.orm.eql.ICompiledSql;
import io.nop.orm.eql.IEqlAstTransformer;
import io.nop.orm.eql.ast.SqlProgram;
import io.nop.orm.eql.ast.SqlQuerySelect;
import io.nop.orm.eql.ast.SqlStatement;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WI12 交付①：真实 h2 方言配置（`h2.dialect.xml` 的 `<forUpdateNowait>`）经生产加载链
 * （DialectManager → DialectImpl → AstToSqlGenerator.printForUpdate）端到端消费——
 * 证明接线不只在手构 DialectModel 上成立（TestEqlNowaitWiring 为手构模型面，
 * 本类为生产配置面；AST 级 nowait 赋值经 IEqlAstTransformer 钩子进入真实 EqlCompiler 管线，
 * Minimum Rules #23 接线验证）。
 */
public class TestEqlNowaitH2Dialect {
    static final String EQL = "select o.id from AppUser o where o.name = 'a' for update";

    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    static class NowaitCtx extends TestEqlCompileSql.TestCompileContext {
        boolean nowait;

        @Override
        public IEqlAstTransformer getAstTransformer() {
            return new IEqlAstTransformer() {
                @Override
                public void transformBeforeAnalyze(SqlProgram ast, String name, String sql,
                                                   ISqlCompileContext context) {
                    SqlStatement stm = ast.getStatements().get(0);
                    if (stm instanceof SqlQuerySelect)
                        ((SqlQuerySelect) stm).setNowait(nowait);
                }

                @Override
                public void transformAfterAnalyze(SqlProgram ast, String name, String sql,
                                                  ISqlCompileContext context) {
                    // no-op
                }
            };
        }
    }

    @Test
    public void testH2ConfigForUpdateNowaitConsumed() {
        IDialect dialect = DialectManager.instance().getDialect("h2");
        assertNotNull(dialect);
        // 生产配置面：h2.dialect.xml 的 <forUpdateNowait> 经继承合并后真实可达
        assertEquals("for update nowait", dialect.getForUpdateNowait());

        assertTrue(sqlOf(dialect, true).endsWith("for update nowait"));
        String plain = sqlOf(dialect, false);
        assertTrue(plain.endsWith("for update"), plain);
        assertFalse(plain.contains("nowait"), plain);
    }

    private static String sqlOf(IDialect dialect, boolean nowait) {
        NowaitCtx ctx = new NowaitCtx();
        ctx.nowait = nowait;
        ctx.entities.put("AppUser", TestEqlCompileSql.entity("AppUser", "APP_USER", "spaceA",
                TestEqlCompileSql.col("id", "SID", 1, true),
                TestEqlCompileSql.col("name", "NAME", 2, false)));
        ctx.effectiveDialect = dialect;
        ICompiledSql compiled = new EqlCompiler().compile("test", EQL, ctx);
        return compiled.getSql().getText().replaceAll("\\s+", " ").trim();
    }
}
