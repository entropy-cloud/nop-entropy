/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.batch.jdbc.loader;

import org.junit.jupiter.api.Test;

import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * plan 2546 R4-12（C7-009 / W-05）核心语义钉：**broken 态必须重抛，不得退化为 EOF 空列表**，
 * 且 **broken 检查必须前置于 isClosed 早退**。
 * <p>
 * 缺陷背景：流式 loader 整任务持有单条物理连接；连接失效后原实现只 close() → 后续 load 走
 * `isClosed()` 早退返回空列表 → 上层当作正常 EOF 静默截断任务（数据丢失面）。修复引入
 * broken 标记并在 close 之前检查，确保故障响亮传播。
 */
public class TestJdbcBatchLoaderBrokenState {

    /** pin05a：broken 后 load 必须重抛同一异常（而非空列表）。 */
    @Test
    public void pin05a_brokenReloadThrowsInsteadOfEmpty() {
        JdbcBatchLoaderProvider<Object> provider = new JdbcBatchLoaderProvider<>();
        JdbcBatchLoaderProvider.LoaderState state = new JdbcBatchLoaderProvider.LoaderState();

        RuntimeException original = new RuntimeException("conn-lost");
        state.markBroken(original);
        state.close();
        assertTrue(state.isClosed(), "markBroken 后应已 close");

        // 关键：即使 state 已 closed（isClosed()==true），也必须先命中 broken 检查并重抛
        RuntimeException thrown = assertThrows(RuntimeException.class,
                () -> provider.load(10, null, state));
        assertSame(original, thrown,
                "broken 态必须重抛原始异常；返回空列表=把故障静默成 EOF（缺陷语义）");
    }

    /** pin05b：未 broken 且已 close（兄弟线程 EOF 面）→ 仍返回空列表（不得误判）。 */
    @Test
    public void pin05b_closedWithoutBrokenStillReturnsEmpty() {
        JdbcBatchLoaderProvider<Object> provider = new JdbcBatchLoaderProvider<>();
        JdbcBatchLoaderProvider.LoaderState state = new JdbcBatchLoaderProvider.LoaderState();

        state.close();
        assertTrue(state.isClosed());
        assertFalse(state.isClosed() && state.getBrokenError() != null,
                "仅 close 不构成 broken");

        assertTrue(provider.load(10, null, state).isEmpty(),
                "EOF 关闭面必须返回空列表（与 broken 严格区分）");
    }

    /** pin05c：broken 标记幂等——首次设置后不被后续异常覆盖（根因保留）。 */
    @Test
    public void pin05c_firstBrokenErrorWins() {
        JdbcBatchLoaderProvider.LoaderState state = new JdbcBatchLoaderProvider.LoaderState();
        RuntimeException first = new RuntimeException("first");
        RuntimeException second = new RuntimeException("second");

        state.markBroken(first);
        state.markBroken(second);
        assertSame(first, state.getBrokenError(), "首个根因必须保留，不被次要异常覆盖");
    }

    /** pin05g：连接失效判定的方言码轨道（不用 SQLState 也能命中）。 */
    @Test
    public void pin05g_dialectErrorCodesRecognized() {
        io.nop.api.core.exceptions.NopException transientErr =
                new io.nop.api.core.exceptions.NopException(
                        io.nop.dao.DaoErrors.ERR_SQL_TRANSIENT_CONNECTION_FAIL);
        assertTrue(JdbcBatchLoaderProvider.isConnectionFailure(transientErr),
                "transient-connection-fail 必须计连接失效（方言映射轨）");

        io.nop.api.core.exceptions.NopException recoverable =
                new io.nop.api.core.exceptions.NopException(
                        io.nop.dao.DaoErrors.ERR_SQL_RECOVERABLE_DATA_ACCESS);
        assertTrue(JdbcBatchLoaderProvider.isConnectionFailure(recoverable));

        // 反向：uncategorized 兜底码不得计连接失效（否则 poison 重试）
        io.nop.api.core.exceptions.NopException uncategorized =
                new io.nop.api.core.exceptions.NopException(
                        io.nop.dao.DaoErrors.ERR_SQL_UNCATEGORIZED_FAILURE);
        assertFalse(JdbcBatchLoaderProvider.isConnectionFailure(uncategorized),
                "uncategorized-failure 是通用兜底，不得置 broken（会永久 poison 重试）");
    }
}
