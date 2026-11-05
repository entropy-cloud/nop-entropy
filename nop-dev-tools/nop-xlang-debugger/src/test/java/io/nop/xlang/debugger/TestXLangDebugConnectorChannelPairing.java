/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.debugger;

import io.nop.api.core.json.JSON;
import io.nop.api.debugger.IBreakpointManager;
import io.nop.api.debugger.Breakpoint;
import io.nop.api.debugger.IDebugger;
import io.nop.api.debugger.IDebuggerAsync;
import io.nop.commons.util.DestroyHelper;
import io.nop.rpc.simple.SimpleRpcServer;
import io.nop.core.lang.json.JsonTool;
import io.nop.socket.ServerConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.Thread.State;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CRA 8.40（6.5 单元）W-02 验收载体红②（C10-002 缺保护面，确定性）：XLangDebugConnector
 * 配对关闭——connect → destroy 后 recv 线程超时内退出（身份快照[m3]非数量基线）、三轮循环
 * 新增线程全部终止、无僵尸重连（接线验证绿锚，非门禁）。每轮新建 connector（含其工厂）——
 * destroy 后工厂 newInstance fail-fast（plan 2523 C1-001 世代令牌），复用非法。
 */
public class TestXLangDebugConnectorChannelPairing {
    /** 与 TestRemoteDebugger :53 的 12345 分立的独立固定端口（surefire 类串行） */
    static final int PORT = 12356;
    /** SimpleRpcClientFactory 执行器线程命名前缀（SimpleRpcClientFactory :136） */
    static final String EXECUTOR_THREAD_PREFIX = "simple-rpc-client-executor-";

    SimpleRpcServer server;
    XLangDebugger localDebugger;
    CollectNotifier notifier;
    /** 服务端 accept 计数（回调须在 start() 前设置——newServer 在 doStart 时快照回调字段） */
    final AtomicInteger serverAccepts = new AtomicInteger();

    @BeforeEach
    public void setUp() {
        JSON.registerProvider(JsonTool.instance());
        server = new SimpleRpcServer();
        server.setOnChannelOpen(ch -> serverAccepts.incrementAndGet());
        server.setServerConfig(new ServerConfig());
        server.getServerConfig().setPort(PORT);
        server.getServerConfig().setIdleTimeout(0);

        localDebugger = new XLangDebugger();
        localDebugger.setUseExternalPath(false);
        notifier = new CollectNotifier();
        localDebugger.setNotifier(notifier);
        server.addServiceImpl(IDebugger.class, localDebugger);
        server.start();
    }

    @AfterEach
    public void tearDown() {
        server.stop();
        DestroyHelper.safeDestroy(localDebugger);
        server.stop();
    }

    static Set<Thread> aliveThreadsByPrefix(Predicate<String> nameFilter) {
        Set<Thread> threads = Thread.getAllStackTraces().keySet();
        Set<Thread> ret = new HashSet<>();
        for (Thread t : threads) {
            if (t != null && nameFilter.test(t.getName())) {
                ret.add(t);
            }
        }
        return ret;
    }

    /** 轮前对线程对象做身份快照，destroy 后断言本轮快照中新增的线程全部 !isAlive（m3：非数量比较） */
    static void awaitRoundThreadsDead(Set<Thread> baseline, String message) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 5000;
        Set<Thread> added;
        do {
            Thread.sleep(50);
            Set<Thread> current = aliveThreadsByPrefix(n -> n.startsWith(EXECUTOR_THREAD_PREFIX));
            added = new HashSet<>(current);
            added.removeAll(baseline);
            boolean allDead = true;
            for (Thread t : added) {
                if (t.isAlive() && t.getState() != State.TERMINATED) {
                    allDead = false;
                    break;
                }
            }
            if (allDead) {
                return;
            }
            assertTrue(System.currentTimeMillis() < deadline, message);
        } while (true);
    }

    @Test
    public void testDestroyClosesChannelAndRecvThreadsExit() throws Exception {
        Set<Thread> cumulativeAdded = new HashSet<>();
        for (int round = 1; round <= 3; round++) {
            // 每轮新建 connector（含其工厂）——destroy 后工厂 newInstance fail-fast，复用非法
            Set<Thread> baseline = aliveThreadsByPrefix(n -> n.startsWith(EXECUTOR_THREAD_PREFIX));

            XLangDebugConnector connector = new XLangDebugConnector(PORT, null, null);
            IDebuggerAsync proxy = connector.connect();
            assertNotNull(proxy, "connect must return the debugger proxy");
            assertThrows(IllegalStateException.class, () -> connector.connect(),
                    "double connect is a programming error and must fail fast");

            connector.destroy();

            // destroy 后工厂世代令牌：同工厂再 connect fail-fast（经工厂 :169-175）
            assertThrows(IllegalStateException.class, () -> connector.connect(),
                    "connect after destroy must fail fast");

            // 本轮新增线程全部在 5s 内终止（pre-fix：destroy 不关 socket，recv 线程钉死于
            // readTimeout=0 的 read——确定性红）；累计新增集合跨三轮不残留
            awaitRoundThreadsDead(baseline, "round " + round + ": recv threads must exit within 5s after destroy");
            Set<Thread> current = aliveThreadsByPrefix(n -> n.startsWith(EXECUTOR_THREAD_PREFIX));
            Set<Thread> addedNow = new HashSet<>(current);
            addedNow.removeAll(baseline);
            cumulativeAdded.addAll(addedNow);
            Set<Thread> stillAlive = new HashSet<>();
            for (Thread t : cumulativeAdded) {
                if (t.isAlive() && t.getState() != State.TERMINATED) {
                    stillAlive.add(t);
                }
            }
            assertEquals(0, stillAlive.size(),
                    "round " + round + ": no executor thread may survive after destroy");
        }
    }

    /**
     * 无僵尸重连（接线验证绿锚，非门禁——pre-fix 红性取决于平台 RetryHelper 对已销毁 timer 的
     * 行为）：connect 后先停服（模拟 debuggee 被杀，进入重连）→ destroy → 同端口重启服务 →
     * 2s 观察窗内<b>服务端</b> accept 计数不增长。观测点在服务端（SimpleRpcServer.setOnChannelOpen）
     * ——执行期发现 N4：客户端侧 connector onChannelOpen 因平台布线缺陷
     * （SimpleRpcClient 构造器覆写 SimpleRpcClientFactory 在 SocketClient 上设置的回调）永不触发，
     * 平台面零触碰 watch-only 登记。
     */
    @Test
    public void testDestroyStopsReconnectLoop() throws Exception {
        XLangDebugConnector connector = new XLangDebugConnector(PORT, null, null);
        IDebuggerAsync debugger = connector.connect();
        // 服务端回调在收到首包时触发（SocketServer.processCommand）——发一次 RPC 使 accept 可观测
        ((IBreakpointManager) debugger).addBreakpoint(Breakpoint.build("a", 1));
        long deadline = System.currentTimeMillis() + 3000;
        while (serverAccepts.get() < 1 && System.currentTimeMillis() < deadline) {
            Thread.sleep(50);
        }
        assertTrue(serverAccepts.get() >= 1, "server must accept the channel on connect");

        // 模拟 debuggee 被杀：停服，进入 autoReconnect 重试（重试连接到已停服的端口会失败——
        // 服务端观测不到）；留一小段重连窗口后 destroy，destroy 时机在服务宕机期
        server.stop();
        Thread.sleep(500);

        connector.destroy();
        int acceptsAtDestroy = serverAccepts.get();

        // 同端口以新 server 实例重启（SimpleRpcServer 不支持 stop 后二次 start），
        // 2s 观察窗内服务端不再收到新连接（destroy 置 closed 后 autoReconnect 短路）
        SimpleRpcServer restartServer = new SimpleRpcServer();
        restartServer.setServerConfig(new ServerConfig());
        restartServer.getServerConfig().setPort(PORT);
        restartServer.getServerConfig().setIdleTimeout(0);
        restartServer.addServiceImpl(IDebugger.class, localDebugger);
        restartServer.setOnChannelOpen(ch -> serverAccepts.incrementAndGet());
        restartServer.start();
        try {
            Thread.sleep(2000);
            assertEquals(acceptsAtDestroy, serverAccepts.get(),
                    "no zombie reconnect may open a new channel after destroy");
        } finally {
            restartServer.stop();
        }
    }

    @Test
    public void testDebuggerServiceUsableOverPairedChannel() throws Exception {
        // 端到端：connect → 走通道调用断点管理 → destroy → 线程回收（基线在 connect 前取——
        // destroy 后取基线会把本轮泄漏线程计入基线，断言空集假绿）
        Set<Thread> baseline = aliveThreadsByPrefix(n -> n.startsWith(EXECUTOR_THREAD_PREFIX));
        XLangDebugConnector connector = new XLangDebugConnector(PORT, null, null);
        IDebuggerAsync debugger = connector.connect();
        IBreakpointManager bpm = debugger;
        bpm.addBreakpoint(Breakpoint.build("a", 1));
        connector.destroy();

        awaitRoundThreadsDead(baseline, "recv threads must exit after destroy");
    }
}
