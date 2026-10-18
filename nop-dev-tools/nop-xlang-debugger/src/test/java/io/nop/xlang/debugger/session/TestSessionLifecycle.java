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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 会话生命周期单测（D3 §3.5）：独立实例/destroy close 语义/shutdownAll/默认会话入表/shutdown 拒绝。
 */
public class TestSessionLifecycle {
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
    public void testCreateSessionProducesIndependentInstances() {
        DebugSessionHandle a = manager.createSession("a");
        DebugSessionHandle b = manager.createSession("b");
        assertNotSame(a.getDebugger(), b.getDebugger());
        assertNotEquals(a.getDebugExecutor(), b.getDebugExecutor());
        // 独立实例的断点表互不可见
        a.getDebugger().addBreakpoint(io.nop.api.debugger.Breakpoint.build("f.xpl", 1));
        assertEquals(1, a.getDebugger().getBreakpoints().size());
        assertEquals(0, b.getDebugger().getBreakpoints().size());
    }

    @Test
    public void testDestroySessionClosesInstanceAndRoutesDestroyed() {
        DebugSessionHandle handle = manager.createSession("doomed");
        XLangDebugger debugger = handle.getDebugger();
        assertNotNull(debugger);
        assertFalse(debugger.isClosed());

        manager.destroySession(handle);

        assertTrue(handle.isDestroyed());
        assertTrue(debugger.isClosed());
        assertNull(manager.getSession("doomed"));
        // 单会话销毁不影响新建其他会话（多会话正常生命周期）
        DebugSessionHandle next = manager.createSession("after");
        assertNotNull(next);
        assertFalse(next.isDestroyed());
    }

    @Test
    public void testShutdownAllClosesAllWebSessionsAndRejectsNew() {
        DebugSessionHandle a = manager.createSession("a");
        DebugSessionHandle b = manager.createSession("b");
        assertFalse(a.getDebugger().isClosed());
        assertFalse(b.getDebugger().isClosed());

        manager.shutdownAll();

        assertTrue(a.getDebugger().isClosed());
        assertTrue(b.getDebugger().isClosed());
        assertTrue(a.isDestroyed());
        assertTrue(b.isDestroyed());
        assertNull(manager.getSession("a"));
        assertNull(manager.getSession("b"));
        // shutdown 后 createSession 拒绝（停机语义）
        assertThrows(IllegalStateException.class, () -> manager.createSession("after"));
    }

    @Test
    public void testDefaultSessionReachableViaGetSession() {
        // item 8 审计移交项①：默认会话句柄入表
        DebugSessionHandle handle = manager.getSession("default");
        assertNotNull(handle);
        assertEquals("default", handle.getSessionId());
        assertSame(handle, manager.getSession("default"));
        assertFalse(handle.isDestroyed());
        assertNotNull(handle.getDebugger());
    }

    @Test
    public void testDestroyDestroyedHandleIsIdempotent() {
        // destroyed 哨兵重复 destroy：不抛错、幂等
        DebugSessionHandle destroyed = manager.destroyedHandle();
        manager.destroySession(destroyed);
        assertTrue(destroyed.isDestroyed());
        assertNotNull(manager.destroyedHandle());
    }

    @Test
    public void testClosedAccessorAfterExplicitClose() {
        DebugSessionHandle handle = manager.createSession("c1");
        handle.getDebugger().close();
        assertTrue(handle.getDebugger().isClosed());
    }
}
