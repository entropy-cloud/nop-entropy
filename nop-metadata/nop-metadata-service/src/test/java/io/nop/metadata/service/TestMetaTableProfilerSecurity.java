/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://github.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.metadata.service;

import io.nop.api.core.ApiErrors;
import io.nop.api.core.exceptions.NopException;
import io.nop.metadata.service.catalog.MetaCatalogCollector;
import io.nop.metadata.service.profiling.MetaTableProfiler;
import io.nop.metadata.service.quality.MetaQualityRuleExecutor;
import io.nop.metadata.service.tableref.TableReference;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AR-01 对抗性回归测试：验证 3 个执行器（profiling/catalog/quality）的 schemaPattern 路径
 * 经过 {@code validateIdentifier} 校验，典型 SQL 注入 payload 必须显式失败（NopException，
 * 不传入 JDBC 层）。
 *
 * <p><b>接线验证</b>：测试通过反射调用 {@code normalizeSchema}（私有静态方法）验证
 * schemaPattern 校验确实在 buildFromClause 调用链上被触达，不只是 buildFromClause 方法存在。
 * 同时 {@link #testProfileEntryPointWired} 直接构造 {@link TableReference}（external 形态）
 * 调用 {@link MetaTableProfiler#profile}，验证 profile → buildFromClause → normalizeSchema 的
 * 完整调用链上 schemaPattern 注入 payload 必须显式失败（验证方法在运行时被调用，
 * 不只是方法存在）。
 */
public class TestMetaTableProfilerSecurity {

    /**
     * 反射调用 {@code normalizeSchema}，把 {@link InvocationTargetException} 解包为底层异常。
     * 这样 {@code assertThrows(NopException.class, ...)} 才能匹配底层 {@link NopException}。
     */
    private static String normalizeSchema(Method m, String schemaPattern) {
        try {
            return (String) m.invoke(null, schemaPattern);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException) {
                throw (RuntimeException) cause;
            }
            if (cause instanceof Error) {
                throw (Error) cause;
            }
            throw new NopException(ApiErrors.ERR_WRAP_EXCEPTION, cause);
        } catch (IllegalAccessException e) {
            throw new NopException(ApiErrors.ERR_WRAP_EXCEPTION, e);
        }
    }

    /**
     * profiling 执行器：典型 SQL 注入 payload 在 schemaPattern 上必须显式失败。
     */
    @Test
    public void testProfilingSchemaPatternInjectionRejected() throws Exception {
        Method m = MetaTableProfiler.class.getDeclaredMethod("normalizeSchema", String.class);
        m.setAccessible(true);
        String[] payloads = {
                "x; DROP TABLE y",
                "mysql.user WHERE 1=1--",
                "schema UNION SELECT password FROM users",
                "schema' OR '1'='1",
                "schema/*comment*/",
                "schema; SHUTDOWN;"
        };
        for (String payload : payloads) {
            NopException ex = assertThrows(NopException.class,
                    () -> normalizeSchema(m, payload),
                    "profiling schemaPattern injection payload must fail: " + payload);
            assertEquals(MetaTableProfiler.ERR_PROFILING_INVALID_IDENTIFIER.getErrorCode(),
                    ex.getErrorCode(),
                    "profiling schemaPattern injection must fail with ERR_PROFILING_INVALID_IDENTIFIER: " + payload);
        }
    }

    /**
     * catalog 执行器：典型 SQL 注入 payload 在 schemaPattern 上必须显式失败。
     */
    @Test
    public void testCatalogSchemaPatternInjectionRejected() throws Exception {
        Method m = MetaCatalogCollector.class.getDeclaredMethod("normalizeSchema", String.class);
        m.setAccessible(true);
        String[] payloads = {
                "x; DROP TABLE y",
                "mysql.user WHERE 1=1--",
                "schema UNION SELECT password FROM users",
                "schema' OR '1'='1",
                "schema/*comment*/"
        };
        for (String payload : payloads) {
            NopException ex = assertThrows(NopException.class,
                    () -> normalizeSchema(m, payload),
                    "catalog schemaPattern injection payload must fail: " + payload);
            assertEquals(MetaCatalogCollector.ERR_CATALOG_INVALID_IDENTIFIER.getErrorCode(),
                    ex.getErrorCode(),
                    "catalog schemaPattern injection must fail with ERR_CATALOG_INVALID_IDENTIFIER: " + payload);
        }
    }

    /**
     * quality 执行器：典型 SQL 注入 payload 在 schemaPattern 上必须显义失败。
     */
    @Test
    public void testQualitySchemaPatternInjectionRejected() throws Exception {
        Method m = MetaQualityRuleExecutor.class.getDeclaredMethod("normalizeSchema", String.class);
        m.setAccessible(true);
        String[] payloads = {
                "x; DROP TABLE y",
                "mysql.user WHERE 1=1--",
                "schema UNION SELECT password FROM users",
                "schema' OR '1'='1",
                "schema/*comment*/"
        };
        for (String payload : payloads) {
            NopException ex = assertThrows(NopException.class,
                    () -> normalizeSchema(m, payload),
                    "quality schemaPattern injection payload must fail: " + payload);
            assertEquals(MetaQualityRuleExecutor.ERR_QUALITY_INVALID_IDENTIFIER.getErrorCode(),
                    ex.getErrorCode(),
                    "quality schemaPattern injection must fail with ERR_QUALITY_INVALID_IDENTIFIER: " + payload);
        }
    }

    /**
     * 合法 schemaPattern（简单标识符）必须通过校验，不被误拒（防过度防御）。
     */
    @Test
    public void testValidSchemaPatternAccepted() throws Exception {
        Method p = MetaTableProfiler.class.getDeclaredMethod("normalizeSchema", String.class);
        p.setAccessible(true);
        assertEquals("PUBLIC", normalizeSchema(p, "PUBLIC"));
        assertEquals("my_schema", normalizeSchema(p, " my_schema "));

        Method c = MetaCatalogCollector.class.getDeclaredMethod("normalizeSchema", String.class);
        c.setAccessible(true);
        assertEquals("PUBLIC", normalizeSchema(c, "PUBLIC"));

        Method q = MetaQualityRuleExecutor.class.getDeclaredMethod("normalizeSchema", String.class);
        q.setAccessible(true);
        assertEquals("PUBLIC", normalizeSchema(q, "PUBLIC"));

        // null/empty 通过（依赖连接默认 schema）
        assertEquals(null, normalizeSchema(p, null));
        assertEquals(null, normalizeSchema(p, ""));
        assertEquals(null, normalizeSchema(p, "   "));
    }

    /**
     * <b>接线验证（anti-hollow）</b>：profile() → buildFromClause → normalizeSchema 完整路径上，
     * schemaPattern 注入 payload 必须在 profile 入口处显式失败。
     *
     * <p>构造 external 形态 {@link TableReference}（物理表名通过校验），
     * schemaPattern 传注入 payload，profile 必须抛 NopException。
     * 验证 {@code validateIdentifier} 在运行时被调用链触达，不只是方法存在。
     */
    @Test
    public void testProfileEntryPointWired() {
        MetaTableProfiler profiler = new MetaTableProfiler();
        TableReference ref = new TableReference(
                TableReference.Kind.EXTERNAL, "mt-test", "T_VALID_TABLE", null,
                null, null, null, null);

        // profile 入口签名要求 Connection + DatabaseMetaData；用 null 占位（不会执行到 JDBC，
        // 因为 normalizeSchema 在 buildFromClause 调用前就会抛 ErrorCode）
        NopException ex = assertThrows(NopException.class,
                () -> profiler.profile(null, null, ref, "x; DROP TABLE y", null, "H2"),
                "profile entrypoint must reject schemaPattern injection before reaching JDBC layer");
        assertEquals(MetaTableProfiler.ERR_PROFILING_INVALID_IDENTIFIER.getErrorCode(),
                ex.getErrorCode(),
                "profile entrypoint schemaPattern injection must fail with ERR_PROFILING_INVALID_IDENTIFIER");
    }

    /**
     * <b>接线验证（catalog）</b>：collectForTable 入口拒绝 schemaPattern 注入（运行时触达）。
     */
    @Test
    public void testCatalogEntryPointWired() {
        MetaCatalogCollector collector = new MetaCatalogCollector();
        TableReference ref = new TableReference(
                TableReference.Kind.EXTERNAL, "mt-test", "T_VALID_TABLE", null,
                null, null, null, null);

        NopException ex = assertThrows(NopException.class,
                () -> collector.collectForTable(null, null, ref, "x; DROP TABLE y", "H2"),
                "collectForTable entrypoint must reject schemaPattern injection");
        assertEquals(MetaCatalogCollector.ERR_CATALOG_INVALID_IDENTIFIER.getErrorCode(),
                ex.getErrorCode(),
                "collectForTable entrypoint schemaPattern injection must fail with ERR_CATALOG_INVALID_IDENTIFIER");
    }

    /**
     * <b>接线验证（quality）</b>：judge 入口（custom_sql 路径不经 schemaPattern，故测 volume 路径）拒绝
     * schemaPattern 注入。volume 路径走 buildFromClause → normalizeSchema，会先于 JDBC 校验。
     */
    @Test
    public void testQualityEntryPointWired() {
        MetaQualityRuleExecutor executor = new MetaQualityRuleExecutor();
        TableReference ref = new TableReference(
                TableReference.Kind.EXTERNAL, "mt-test", "T_VALID_TABLE", null,
                null, null, null, null);

        NopException ex = assertThrows(NopException.class,
                () -> executor.judge(null, ref, "x; DROP TABLE y",
                        "volume", "table", null, null, null, "H2"),
                "judge(volume) entrypoint must reject schemaPattern injection");
        assertEquals(MetaQualityRuleExecutor.ERR_QUALITY_INVALID_IDENTIFIER.getErrorCode(),
                ex.getErrorCode(),
                "judge entrypoint schemaPattern injection must fail with ERR_QUALITY_INVALID_IDENTIFIER");
    }

    /**
     * <b>防误拒</b>：profile 入口传合法 schemaPattern 必须进入 JDBC 路径（这里 Connection=null 会
     * 抛 NPE/其它异常，但 ErrorCode 必须不是 INVALID_IDENTIFIER，证明校验已通过）。
     */
    @Test
    public void testProfileValidSchemaNotRejectedByIdentifier() {
        MetaTableProfiler profiler = new MetaTableProfiler();
        TableReference ref = new TableReference(
                TableReference.Kind.EXTERNAL, "mt-test", "T_VALID_TABLE", null,
                null, null, null, null);
        Throwable thrown = null;
        try {
            profiler.profile(null, null, ref, "PUBLIC", null, "H2");
        } catch (Throwable t) {
            thrown = t;
        }
        assertTrue(thrown != null, "profile with null Connection must throw something");
        if (thrown instanceof NopException) {
            NopException ne = (NopException) thrown;
            assertTrue(!MetaTableProfiler.ERR_PROFILING_INVALID_IDENTIFIER.getErrorCode().equals(ne.getErrorCode()),
                    "valid schema PUBLIC must NOT be rejected by ERR_PROFILING_INVALID_IDENTIFIER");
        }
    }
}
