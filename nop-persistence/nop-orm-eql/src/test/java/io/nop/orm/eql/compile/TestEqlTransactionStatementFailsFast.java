/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical_entropy
 * Gitee:  https://gitee.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.eql.compile;

import io.nop.api.core.exceptions.NopException;
import io.nop.commons.type.StdSqlType;
import io.nop.dao.dialect.IDialect;
import io.nop.dao.dialect.impl.DialectImpl;
import io.nop.dao.dialect.model.DialectFeatures;
import io.nop.dao.dialect.model.DialectModel;
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

import static io.nop.orm.eql.OrmEqlErrors.ERR_EQL_NOT_SUPPORT_TRANSACTION_STATEMENT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plan 2307 Phase 4 / V4：事务控制语句的编译期<strong>显式失败</strong>。
 *
 * <p>缺陷基线：{@code commit} / {@code rollback} 能一路走到输出阶段，
 * 而两个输出器都没有它们的 printer，于是产出<strong>空字符串</strong> SQL 且不报任何错误
 * ——一条静默无操作。修复后必须带明确错误码失败。
 *
 * <p>失败点选在 {@code EqlCompiler.compile} 的 transform 之前：尽早失败，
 * 且不产生任何半成品 SQL。
 */
/**
 * <p><b>不继承 {@link TestEqlCompileSql}</b>：继承具体测试类会让父类全部 {@code @Test}
 * 在子类名下重跑，测试数虚增、失败定位变模糊。
 */
public class TestEqlTransactionStatementFailsFast {

    static IDialect dialect;

    @BeforeAll
    public static void initDialect() {
        DialectModel model = new DialectModel();
        DialectFeatures features = new DialectFeatures();
        model.setFeatures(features);
        model.setReservedKeywords(Collections.emptySet());
        model.setSqls(new io.nop.dao.dialect.model.DialectSqls());
        model.setSqlDataTypes(Collections.emptyList());
        io.nop.dao.dialect.model.SqlNativeFunctionModel fn =
                new io.nop.dao.dialect.model.SqlNativeFunctionModel();
        fn.setName("current_timestamp");
        fn.setHasParenthesis(false);
        fn.setReturnType(StdSqlType.TIMESTAMP);
        model.setFunctions(Collections.singletonList(fn));
        dialect = new DialectImpl(model);
    }

    static OrmColumnModel col2(String name, String code, int propId, boolean primary) {
        OrmColumnModel c = new OrmColumnModel();
        c.setName(name);
        c.setCode(code);
        c.setPropId(propId);
        c.setStdSqlType(StdSqlType.VARCHAR);
        c.setPrimary(primary);
        return c;
    }

    static OrmEntityModel entity2(String name, String tableName, OrmColumnModel... cols) {
        OrmEntityModel m = new OrmEntityModel();
        m.setName(name);
        m.setTableName(tableName);
        m.setQuerySpace("spaceA");
        m.setColumns(new ArrayList<>(Arrays.asList(cols)));
        m.init();
        return m;
    }

    static class Ctx implements ISqlCompileContext {
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
            return m == null ? null : new EntityTableMeta(m, null, dialect);
        }

        @Override
        public IAliasGenerator getAliasGenerator() {
            return new SeqAliasGenerator();
        }
    }

    static Ctx ctx() {
        Ctx c = new Ctx();
        c.entities.put("AppUser", entity2("AppUser", "APP_USER",
                col2("id", "SID", 1, true), col2("name", "NAME", 2, false)));
        return c;
    }

    // ---- 核心：必须带错误码失败，而不是编译出空 SQL ----

    @Test
    public void testCommitFailsFast() {
        NopException e = assertThrows(NopException.class,
                () -> new EqlCompiler().compile("test", "commit", ctx()));
        assertEquals(ERR_EQL_NOT_SUPPORT_TRANSACTION_STATEMENT.getErrorCode(), e.getErrorCode());
    }

    @Test
    public void testRollbackFailsFast() {
        NopException e = assertThrows(NopException.class,
                () -> new EqlCompiler().compile("test", "rollback", ctx()));
        assertEquals(ERR_EQL_NOT_SUPPORT_TRANSACTION_STATEMENT.getErrorCode(), e.getErrorCode());
    }

    @Test
    public void testCommitCaseInsensitiveFailsFast() {
        NopException e = assertThrows(NopException.class,
                () -> new EqlCompiler().compile("test", "COMMIT", ctx()));
        assertEquals(ERR_EQL_NOT_SUPPORT_TRANSACTION_STATEMENT.getErrorCode(), e.getErrorCode());
    }

    @Test
    public void testErrorMessageNamesStatementKind() {
        NopException e = assertThrows(NopException.class,
                () -> new EqlCompiler().compile("test", "rollback", ctx()));
        assertEquals("ROLLBACK", e.getParam("statementKind"));
    }

    @Test
    public void testErrorMessagePointsToTransactionApi() {
        // 错误信息必须指向正确 API，否则用户只知道"不支持"却不知道该用什么
        NopException e = assertThrows(NopException.class,
                () -> new EqlCompiler().compile("test", "commit", ctx()));
        assertTrue(String.valueOf(e.getMessage()).contains("ITransactionTemplate"),
                "错误信息应指向 ITransactionTemplate: " + e.getMessage());
    }

    @Test
    public void testNoEmptySqlIsProduced() {
        // 回归钉：修复前 commit 编译成功且 SQL 为空字符串（COMPILE-OK sql=[]）
        assertThrows(NopException.class, () -> new EqlCompiler().compile("test", "commit", ctx()));
    }

    // ---- 防误伤：普通 DML 不受影响 ----

    @Test
    public void testSelectStillCompiles() {
        assertNotNull(new EqlCompiler().compile("test", "select o.name from AppUser o", ctx()).getSql());
    }

    @Test
    public void testUpdateStillCompiles() {
        assertNotNull(new EqlCompiler().compile("test",
                "update AppUser o set o.name = 'x' where o.id = '1'", ctx()).getSql());
    }

    @Test
    public void testDeleteStillCompiles() {
        assertNotNull(new EqlCompiler().compile("test",
                "delete from AppUser o where o.id = '1'", ctx()).getSql());
    }

    @Test
    public void testInsertStillCompiles() {
        assertNotNull(new EqlCompiler().compile("test",
                "insert into AppUser(id, name) values ('1', 'a')", ctx()).getSql());
    }
}