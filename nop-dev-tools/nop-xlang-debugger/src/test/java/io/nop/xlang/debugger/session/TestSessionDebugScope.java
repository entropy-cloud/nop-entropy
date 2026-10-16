/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy/nop-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.debugger.session;

import io.nop.xlang.debugger.XLangDebugger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 标记契约单测：save-restore 嵌套、sessionId miss/null 归一 destroyed、destroyed 位翻转路由。
 */
public class TestSessionDebugScope {
    private SessionDebugManager manager;

    @BeforeEach
    public void setUp() {
        manager = SessionDebugManager.instance();
        manager.init(new XLangDebugger());
    }

    @AfterEach
    public void tearDown() {
        DebugSessionScope.clear();
        manager.reset();
    }

    @Test
    public void testNoMarkerYieldsNullHandle() {
        assertNull(DebugSessionScope.currentHandle());
    }

    @Test
    public void testWithSessionBindsAndRestores() {
        DebugSessionHandle handle = manager.createSession("s1", new XLangDebugger());
        AtomicReference<DebugSessionHandle> seen = new AtomicReference<>();
        String result = DebugSessionScope.withSession(handle, () -> {
            seen.set(DebugSessionScope.currentHandle());
            return "ok";
        });
        assertEquals("ok", result);
        assertEquals(handle, seen.get().getSessionId() == null ? null : seen.get());
        assertNull(DebugSessionScope.currentHandle());
    }

    @Test
    public void testNestedSaveRestore() {
        DebugSessionHandle outer = manager.createSession("outer", new XLangDebugger());
        DebugSessionHandle inner = manager.createSession("inner", new XLangDebugger());

        DebugSessionScope.withSession(outer, () -> {
            DebugSessionScope.withSession(inner, (Runnable) () -> {
                assertSame(inner, DebugSessionScope.currentHandle());
            });
            // 内层退出后恢复外层标记（save-restore）
            assertSame(outer, DebugSessionScope.currentHandle());
        });
        assertNull(DebugSessionScope.currentHandle());
    }

    @Test
    public void testNestedRestoreOnException() {
        DebugSessionHandle outer = manager.createSession("outer", new XLangDebugger());
        DebugSessionHandle inner = manager.createSession("inner", new XLangDebugger());
        try {
            DebugSessionScope.withSession(outer, () -> {
                try {
                    DebugSessionScope.withSession(inner, () -> {
                        throw new IllegalStateException("boom");
                    });
                } catch (IllegalStateException e) {
                    // 异常路径同样恢复外层标记
                    assertSame(outer, DebugSessionScope.currentHandle());
                    throw e;
                }
            });
        } catch (IllegalStateException expected) {
            // fall through
        }
        assertNull(DebugSessionScope.currentHandle());
    }

    @Test
    public void testSessionIdMissNormalizesToDestroyed() {
        DebugSessionHandle destroyed = manager.destroyedHandle();
        AtomicReference<DebugSessionHandle> seen = new AtomicReference<>();
        DebugSessionScope.withSession("not-exist", () -> seen.set(DebugSessionScope.currentHandle()));
        // miss 归一 destroyed 哨兵：绝不落默认会话（防泄漏进 IDEA 调试面）
        assertEquals(destroyed, seen.get());
        assertTrue(seen.get().isDestroyed() || "__destroyed__".equals(seen.get().getSessionId()));
    }

    @Test
    public void testNullHandleNormalizesToDestroyed() {
        AtomicReference<DebugSessionHandle> seen = new AtomicReference<>();
        DebugSessionScope.withSession((DebugSessionHandle) null, () -> seen.set(DebugSessionScope.currentHandle()));
        assertSame(manager.destroyedHandle(), seen.get());
    }

    @Test
    public void testDestroyedBitFlipsRoutingInput() {
        DebugSessionHandle handle = manager.createSession("doomed", new XLangDebugger());
        manager.destroySession(handle);
        assertTrue(handle.isDestroyed());
        // 销毁后 getSession 不再返回（已注销）
        assertNull(manager.getSession("doomed"));
        // 销毁后的句柄不再是默认会话
        assertNotEquals(manager.defaultExecutor(), handle.getDebugExecutor());
    }
}
