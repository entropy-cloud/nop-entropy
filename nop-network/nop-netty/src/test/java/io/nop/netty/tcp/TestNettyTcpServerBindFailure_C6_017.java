package io.nop.netty.tcp;

import io.nop.netty.config.NettyTcpServerConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.lang.management.ManagementFactory;
import java.net.ServerSocket;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * C6-017 (plan 2523)：bind 失败必须释放事件循环组——「启动失败→再启动」循环不再净增
 * 2×CPU 个非 daemon I/O 线程（当前即红：doStart 两抛出点零清理）。
 */
@Timeout(60)
public class TestNettyTcpServerBindFailure_C6_017 {

    static boolean nonDaemonThreadCountAvailable() {
        return ManagementFactory.getThreadMXBean() != null;
    }

    private long nonDaemonThreadCount() {
        long count = 0;
        for (java.lang.management.ThreadInfo info : ManagementFactory.getThreadMXBean().dumpAllThreads(false, false)) {
            if (!info.isDaemon())
                count++;
        }
        return count;
    }

    private NettyTcpServer newServerOnPort(int port) {
        NettyServerConfig config = new NettyTcpServerConfig();
        config.setHost("127.0.0.1");
        config.setPort(port);
        config.setBossGroupSize(1);
        config.setWorkerGroupSize(2);
        return new NettyTcpServer(config);
    }

    @Test
    public void bindFailureMustReleaseEventLoopGroups() throws Exception {
        // 占用端口制造 bind 失败
        ServerSocket blocker = new ServerSocket(0);
        int port = blocker.getLocalPort();
        try {
            NettyTcpServer server = newServerOnPort(port);
            long before = nonDaemonThreadCount();
            // 失败模式 = 异常抛出（bind-port-fail 两条路径）
            assertThrows(Exception.class, server::start,
                    "bind on an occupied port must throw");
            Thread.sleep(200); // shutdownGracefully 异步——给一点归集时间
            long afterFirst = nonDaemonThreadCount();
            assertTrue(afterFirst <= before + 2,
                    "C6-017: bind failure must release boss/worker event loop groups"
                            + " (defect: " + (afterFirst - before) + " non-daemon threads retained)");

            // 再启动循环不再线性累积
            for (int i = 0; i < 3; i++) {
                NettyTcpServer retry = newServerOnPort(port);
                try {
                    retry.start();
                } catch (Exception expected) {
                    // ignore
                }
            }
            Thread.sleep(200);
            long afterLoop = nonDaemonThreadCount();
            assertTrue(afterLoop <= afterFirst + 2,
                    "C6-017: repeated start failures must not accumulate I/O threads: "
                            + (afterLoop - afterFirst) + " extra threads over 3 retries");
        } finally {
            blocker.close();
        }
    }
}
