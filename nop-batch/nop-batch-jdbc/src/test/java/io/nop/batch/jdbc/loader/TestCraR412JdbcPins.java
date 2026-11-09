/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.batch.jdbc.loader;

import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.beans.LongRangeBean;
import io.nop.batch.core.IBatchChunkContext;
import io.nop.batch.core.impl.BatchTaskContextImpl;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.sql.SQLNonTransientConnectionException;
import java.sql.SQLRecoverableException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * plan 2546 R4-12（C7-008 / W-04、C7-009 / W-05）jdbc 侧载体。
 */
public class TestCraR412JdbcPins {

    // ==================== W-05 / C7-009：连接级失效判定 ====================

    /** pin05d 轨道①：SQLState 08 前缀（PG 连接被杀 08006）→ 计连接失效。 */
    @Test
    public void pin05d_sqlState08IsConnectionFailure() {
        assertTrue(JdbcBatchLoaderProvider.isConnectionFailure(new SQLException("connection closed", "08006")),
                "SQLState 08006 必须计为连接级失效（PG 形态的轨道①）");
    }

    /** pin05d 反向：未映射业务 SQL 错误（22012）**不得**计连接失效（否则永久 poison 重试）。 */
    @Test
    public void pin05d_uncategorizedBusinessErrorIsNotConnectionFailure() {
        assertFalse(JdbcBatchLoaderProvider.isConnectionFailure(new SQLException("division by zero", "22012")),
                "未映射的业务 SQL 错误不得置 broken——否则会永久 poison 掉重试机会");
    }

    /** pin05e：类型轨（SQLRecoverableException / SQLNonTransientConnectionException）命中。 */
    @Test
    public void pin05e_typedConnectionExceptions() {
        assertTrue(JdbcBatchLoaderProvider.isConnectionFailure(new SQLRecoverableException("recoverable")));
        assertTrue(JdbcBatchLoaderProvider.isConnectionFailure(new SQLNonTransientConnectionException("conn")));
    }

    /** pin05f：SQLState 为 null 时不得 NPE（防护）。 */
    @Test
    public void pin05f_nullSqlStateSafe() {
        assertFalse(JdbcBatchLoaderProvider.isConnectionFailure(new SQLException("no state")));
    }

    // ==================== W-04 / C7-008：batchSize 守卫 ====================

    /**
     * pin04：load(batchSize<=0) 必须 fail-fast——真实驱动 package-private load，
     * 不是常量自证（缺陷态：0/负值会穿透到 offset 翻页，产生无意义查询）。
     */
    @Test
    public void pin04_nonPositiveBatchSizeFailsFast() throws Exception {
        JdbcPageBatchLoaderProvider<Object> provider = new JdbcPageBatchLoaderProvider<>();
        provider.setSql(io.nop.core.lang.sql.SQL.begin().sql("select 1").end());

        Object state = newStateOf(provider);
        java.lang.reflect.Method load = JdbcPageBatchLoaderProvider.class
                .getDeclaredMethod("load", int.class, IBatchChunkContext.class,
                        Class.forName("io.nop.batch.jdbc.loader.JdbcPageBatchLoaderProvider$LoaderState"));
        load.setAccessible(true);

        IBatchChunkContext chunkCtx = new BatchTaskContextImpl().newChunkContext();
        java.lang.reflect.InvocationTargetException ite = assertThrows(
                java.lang.reflect.InvocationTargetException.class,
                () -> load.invoke(provider, 0, chunkCtx, state));
        assertTrue(ite.getCause() instanceof NopException, "必须是 NopException");
        assertEquals("nop.err.batch.batch-size-invalid",
                ((NopException) ite.getCause()).getErrorCode());
    }

    private static Object newStateOf(JdbcPageBatchLoaderProvider<?> provider) throws Exception {
        Class<?> stateCls = Class.forName("io.nop.batch.jdbc.loader.JdbcPageBatchLoaderProvider$LoaderState");
        java.lang.reflect.Constructor<?> ctor = stateCls.getDeclaredConstructor();
        ctor.setAccessible(true);
        return ctor.newInstance();
    }
}
