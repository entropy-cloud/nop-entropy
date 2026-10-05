/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical_entropy
 * Gitee:  https://gitee.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.dao.dialect;

import io.nop.core.lang.sql.SQL;
import io.nop.dao.DaoErrors;
import io.nop.dao.dialect.exception.ISQLExceptionTranslator;
import io.nop.dao.dialect.impl.DialectImpl;
import io.nop.dao.dialect.model.DialectFeatures;
import io.nop.dao.dialect.model.DialectModel;
import io.nop.dao.dialect.model.DialectSqls;
import io.nop.dao.dialect.model.SqlNativeFunctionModel;
import io.nop.commons.type.StdSqlType;
import io.nop.dao.exceptions.JdbcException;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plan 2307 Phase 6 / V8b：方言 {@code <sqlExceptionTranslator>} 配置必须真正生效。
 *
 * <p>缺陷：{@code DialectImpl} 构造函数原先硬编码
 * {@code new DialectSQLExceptionTranslator(dialectModel)}，
 * <strong>从不读</strong> {@code dialectModel.getSqlExceptionTranslator()}——
 * 而 {@code default.dialect.xml:9} 又配置了该 class-name。
 * 结果是使用者在自有 dialect xml 中覆盖它会被<strong>静默忽略</strong>：
 * 配置面存在、功能是活的，但配置值不起作用（contract drift）。
 *
 * <p>与 {@code <upsertHandler>} 的区别：后者零消费方、零配置、无任何功能，是纯幽灵节点；
 * 前者功能是活的（{@code ISQLExceptionTranslator} 被 {@code JdbcTemplateImpl} 等 10+ 处消费），
 * 只是绕过了模型字段。故前者接线、后者删除。
 */
public class TestDialectSqlExceptionTranslatorWiring {

    /**
     * 测试用自定义翻译器：必须有无参构造才能被反射实例化。
     * 它的行为与默认实现刻意不同（返回 SQL_BAD_SQL_GRAMMAR），
     * 这样「是否真的走了自定义实现」可由错误码直接判定。
     */
    public static class CustomTranslator implements ISQLExceptionTranslator {
        public static volatile boolean instantiated;

        public CustomTranslator() {
            instantiated = true;
        }

        @Override
        public JdbcException translate(SQL sql, SQLException ex) {
            return marker();
        }

        @Override
        public JdbcException translate(String action, SQLException ex) {
            return marker();
        }

        private JdbcException marker() {
            return new JdbcException(DaoErrors.ERR_SQL_BAD_SQL_GRAMMAR);
        }
    }

    static DialectModel model(String translatorClass) {
        DialectModel m = new DialectModel();
        m.setFeatures(new DialectFeatures());
        m.setReservedKeywords(Collections.emptySet());
        m.setSqls(new DialectSqls());
        m.setSqlDataTypes(Collections.emptyList());
        // DialectImpl 构造末尾会调 buildFunctionSql("current_timestamp")，
        // 该函数未登记时直接 NPE（与本测试目的无关，但必须登记才能构造方言）
        SqlNativeFunctionModel currentTimestamp = new SqlNativeFunctionModel();
        currentTimestamp.setName("current_timestamp");
        currentTimestamp.setHasParenthesis(false);
        currentTimestamp.setReturnType(StdSqlType.TIMESTAMP);
        m.setFunctions(Collections.singletonList(currentTimestamp));
        if (translatorClass != null)
            m.setSqlExceptionTranslator(translatorClass);
        return m;
    }

    @Test
    public void testConfiguredTranslatorIsHonored() {
        // 核心回归：修复前该配置被静默忽略
        CustomTranslator.instantiated = false;
        DialectImpl dialect = new DialectImpl(model(CustomTranslator.class.getName()));
        assertTrue(CustomTranslator.instantiated,
                "方言 xml 中配置的 sqlExceptionTranslator 必须被实例化");
        assertInstanceOf(CustomTranslator.class, dialect.getSQLExceptionTranslator(),
                "方言必须使用配置的翻译器实现");
    }

    @Test
    public void testConfiguredTranslatorIsActuallyUsed() {
        // 不止"被实例化"，还必须真的参与异常翻译
        DialectImpl dialect = new DialectImpl(model(CustomTranslator.class.getName()));
        JdbcException err = dialect.getSQLExceptionTranslator()
                .translate("select 1", new SQLException("boom"));
        assertEquals(DaoErrors.ERR_SQL_BAD_SQL_GRAMMAR.getErrorCode(), err.getErrorCode(),
                "异常翻译必须走自定义实现");
    }

    @Test
    public void testConfiguredTranslatorUsedForSqlOverload() {
        DialectImpl dialect = new DialectImpl(model(CustomTranslator.class.getName()));
        JdbcException err = dialect.getSQLExceptionTranslator()
                .translate(SQL.begin().sql("select 1").end(), new SQLException("boom"));
        assertEquals(DaoErrors.ERR_SQL_BAD_SQL_GRAMMAR.getErrorCode(), err.getErrorCode());
    }

    @Test
    public void testUnconfiguredTranslatorFallsBackToDefault() {
        // 未配置时行为必须与修复前完全一致（否则会打破所有既有方言）
        DialectImpl dialect = new DialectImpl(model(null));
        assertNotNull(dialect.getSQLExceptionTranslator());
        assertEquals("io.nop.dao.dialect.impl.DialectSQLExceptionTranslator",
                dialect.getSQLExceptionTranslator().getClass().getName());
    }

    @Test
    public void testEmptyTranslatorFallsBackToDefault() {
        DialectImpl dialect = new DialectImpl(model(""));
        assertEquals("io.nop.dao.dialect.impl.DialectSQLExceptionTranslator",
                dialect.getSQLExceptionTranslator().getClass().getName());
    }

    @Test
    public void testDefaultTranslatorStillWorks() {
        // 回归钉：默认实现仍能正常翻译（未被接线改动破坏）
        DialectImpl dialect = new DialectImpl(model(null));
        assertNotNull(dialect.getSQLExceptionTranslator().translate("select 1", new SQLException("boom")));
    }

    @Test
    public void testTranslatorIsStableAcrossCalls() {
        DialectImpl dialect = new DialectImpl(model(CustomTranslator.class.getName()));
        assertSame(dialect.getSQLExceptionTranslator(), dialect.getSQLExceptionTranslator(),
                "翻译器应在方言生命周期内保持同一实例");
    }
}