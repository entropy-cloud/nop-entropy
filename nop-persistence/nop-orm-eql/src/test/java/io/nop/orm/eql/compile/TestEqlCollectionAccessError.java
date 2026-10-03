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

import static io.nop.orm.eql.OrmEqlErrors.ERR_EQL_COLLECTION_ACCESS_NOT_SUPPORT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WI1 BUG-14：集合下标表达式产出垃圾 SQL（`select o.roles[1].x()` →
 * `select o.roles 1 from t as o`，静默损坏、零信号）。
 *
 * <p>裁定（`ai-dev/design/eql-cross-db-compat/2026-10-03-wi1-class-a-adjudications.md` §2）
 * = 候选 B 显式报错：`sqlCollectionAccessExpr` 的 `[...]` 是集合过滤语义（非下标），
 * 编译管线无 lowering，机械回显没有正确输出目标 ⇒ 两个生成器（基类同门）显式抛
 * {@code ERR_EQL_COLLECTION_ACCESS_NOT_SUPPORT}，不再静默产出错误 SQL。
 * 今天已失败的输入（`o.roles[]`、`o.roles[order by a].x()`——文法层失败）保持响亮失败；
 * 集合操作符 `_some`/`_all` 走另一条 AST 路径，不受影响。
 */
public class TestEqlCollectionAccessError extends BaseTestCase {
    static IDialect dialect;

    @BeforeAll
    public static void init() {
        dialect = buildDialect();
    }

    static IDialect buildDialect() {
        DialectModel model = new DialectModel();
        model.setReservedKeywords(Collections.emptySet());
        io.nop.dao.dialect.model.DialectSqls sqls = new io.nop.dao.dialect.model.DialectSqls();
        model.setSqls(sqls);
        model.setSqlDataTypes(Collections.emptyList());
        model.setFeatures(new io.nop.dao.dialect.model.DialectFeatures());
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
        ctx.entities.put("AppUser", entity("AppUser", "APP_USER",
                col("id", "SID", 1, true), col("name", "NAME", 2, false)));
        return ctx;
    }

    static String sqlOf(String eql) {
        TestCompileContext ctx = baseContext();
        ctx.effectiveDialect = dialect;
        return new EqlCompiler().compile("test", eql, ctx).getSql().getText().replaceAll("\\s+", " ");
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
    public void testEchoPathExplicitlyRejectsCollectionAccess() {
        // 修复前红：echo 输出 `select o.roles 1 from t as o`（垃圾文本、零信号）
        NopException e = assertThrows(NopException.class,
                () -> new EqlASTParser().parseFromText(null, "select o.roles[1].x() from t o").toSqlString());
        assertEquals(ERR_EQL_COLLECTION_ACCESS_NOT_SUPPORT.getErrorCode(), e.getErrorCode());
    }

    @Test
    public void testExecutionPathExplicitlyRejectsCollectionAccess() {
        // 修复前红：执行路径生成器（AstToSqlGenerator，EqlCompiler 的 SQL 产出环节，
        // 继承基类 override）此前零处理、静默产出垃圾。直接驱动生成器访问该 AST 节点
        // 证明执行路径的分派真实到达显式报错（未解析投影在生成器先于子节点失败，
        // 全链路响亮行为另见 testEntityStageAlsoLoud）。
        SqlProgram program = new EqlASTParser().parseFromText(null, "select o.roles[1].x() from t o");
        io.nop.orm.eql.ast.SqlCollectionAccessExpr coll = findCollectionAccess(program);
        assertNotNull(coll, "collection access expr must be built by the parser");
        io.nop.orm.eql.sql.AstToSqlGenerator gen = new io.nop.orm.eql.sql.AstToSqlGenerator(dialect, false);
        NopException e = assertThrows(NopException.class, () -> gen.visit(coll));
        assertEquals(ERR_EQL_COLLECTION_ACCESS_NOT_SUPPORT.getErrorCode(), e.getErrorCode());
    }

    static io.nop.orm.eql.ast.SqlCollectionAccessExpr findCollectionAccess(io.nop.orm.eql.ast.EqlASTNode node) {
        if (node instanceof io.nop.orm.eql.ast.SqlCollectionAccessExpr)
            return (io.nop.orm.eql.ast.SqlCollectionAccessExpr) node;
        io.nop.orm.eql.ast.SqlCollectionAccessExpr[] found = new io.nop.orm.eql.ast.SqlCollectionAccessExpr[1];
        node.forEachChild(child -> {
            if (found[0] == null)
                found[0] = findCollectionAccess(child);
        });
        return found[0];
    }

    @Test
    public void testEntityStageAlsoLoud() {
        // 全链路（EqlCompiler）：实体解析层对未知属性同样在编译期响亮拒绝——
        // 文法放开后不存在任何"静默错误 SQL"阶段
        NopException e = assertThrows(NopException.class,
                () -> sqlOf("select o.roles[1].x() from AppUser o"));
        assertTrue(e.getErrorCode().startsWith("nop.err.eql."),
                "compile stage must fail loudly, got: " + e.getErrorCode());
    }

    @Test
    public void testAlreadyFailingInputsStayLoud() {
        // `o.roles[]` / `o.roles[order by a].x()`：今天文法层失败，保持响亮失败
        assertThrows(NopException.class,
                () -> new EqlASTParser().parseFromText(null, "select o.roles[] from t o"));
        NopException e = assertThrows(NopException.class,
                () -> new EqlASTParser().parseFromText(null, "select o.roles[order by a].x() from t o"));
        assertTrue(e.getErrorCode().startsWith("nop.err.antlr"),
                "grammar-level failures keep antlr error codes, got: " + e.getErrorCode());
    }
}
