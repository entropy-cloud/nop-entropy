package io.nop.netty.tcp;

import io.nop.netty.config.NettyTcpServerConfig;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * C6-017 (plan 2523)：bind 失败两抛出点必须调用 releaseBindFailure 释放事件循环组。
 *
 * <p>证据形态：源码级结构钉。行为级线程计数探针经变异实证**不可判别**——Netty
 * NioEventLoopGroup 线程惰性启动（bind 失败前 worker 线程常未启动），泄漏组无存活线程时
 * 随 GC 回收，非 daemon 线程计数与 event-loop 线程名计数在缺陷态均不超界（变异探针 v1/v2
 * 实证，见 `_tmp/red-green-2523-red-netty-v2.log`）。结构在位即判定：两失败路径清单一语义
 * 对齐 doStop。</p>
 */
public class TestNettyTcpServerBindFailure_C6_017 {

    @Test
    public void bothBindFailurePathsMustReleaseEventLoopGroups() throws Exception {
        Path source = Path.of("src/main/java/io/nop/netty/tcp/NettyTcpServer.java");
        String code = Files.readString(source);
        long calls = code.lines().filter(l -> l.contains("releaseBindFailure(bossGroup, workerGroup, ownWorkerGroup, e);"))
                .count();
        assertTrue(calls >= 2,
                "C6-017: both bind-failure throw paths (sync-exception + !isSuccess) must call"
                        + " releaseBindFailure — found " + calls + " of 2 (defect: zero cleanup on"
                        + " start-fail-retry loops leaks 2×CPU I/O threads per iteration)");
        assertTrue(code.contains("private void releaseBindFailure("),
                "cleanup method must exist");
        assertTrue(code.contains("shutdownGracefully()"),
                "cleanup must shutdown the event loop groups");
        assertTrue(code.contains("globalTrafficShapingHandler.release()"),
                "cleanup must release the traffic shaping handler (doStop 清单语义对齐)");
    }
}
