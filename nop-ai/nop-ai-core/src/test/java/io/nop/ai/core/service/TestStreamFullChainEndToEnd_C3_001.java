package io.nop.ai.core.service;

import io.nop.ai.api.chat.ChatOptions;
import io.nop.ai.api.chat.ChatRequest;
import io.nop.ai.api.chat.ChatResponse;
import io.nop.api.core.config.AppConfig;
import io.nop.api.core.exceptions.NopException;
import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.http.api.client.HttpClientConfig;
import io.nop.http.client.jdk.JdkHttpClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CancellationException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static io.nop.ai.core.NopAiCoreErrors.ERR_AI_STREAM_TERMINATION_SIGNAL_MISSING;
import static io.nop.ai.core.NopAiCoreErrors.ERR_AI_STREAM_TOTAL_TIMEOUT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * plan 2505 Phase 3 两侧同批端到端验证（4.3 merge-adjudication §七-6 验收面，rule 22）：
 * 缺省装配（http-api beans 的 raw client = {@link JdkHttpClient}，此处按
 * {@code TestChatServiceImpl} 先例直构装配）下 {@code ChatServiceImpl.callAsync(stream=true)}
 * 真实全链三场景有界终止：
 * <ul>
 *   <li>① 取消场景：流进行中 {@code cancelToken.cancel()} → 聚合 future 在 2s 观察窗内
 *       到达可区分的取消终态，且服务端观测到客户端 EOF（body 读线程释放、底层流关闭）</li>
 *   <li>② 挂死场景：真实 JdkHttpClient + 本地静默 SSE 服务端，双层预算分别独立断言——
 *       聚合层流总量期限（{@code nop.ai.service.stream-total-timeout}）与传输层 body idle
 *       读预算（{@code nop.http.client.jdk.body-idle-timeout}）各自可在全链上终态化</li>
 *   <li>③ 半关闭场景：部分 chunk 后服务端干净断连（TCP FIN，无 finish_reason/[DONE]）→
 *       非 success 终态（R11 纪律，completeExceptionally）</li>
 * </ul>
 *
 * <p>静默服务端采用「headers + 首 chunk 后停滞」而非纯 accept 不回发：纯 accept 形态挂死在
 * headers 阶段（body 阶段预算不可达），headers-后-静默形态使双层预算均有独立观测点。
 * 组件级 fake 传输的红转绿由 {@code TestStreamCancelDeadlineAndDelivery_C3_001}（Phase 2）与
 * {@code TestBodyPhaseCancel_C3_002}（Phase 1）承载，本类不重复先红后绿义务。
 */
public class TestStreamFullChainEndToEnd_C3_001 extends JunitBaseTestCase {

    private static final String CFG_STREAM_TOTAL_TIMEOUT = "nop.ai.service.stream-total-timeout";
    private static final String CFG_STREAM_IDLE_TIMEOUT = "nop.ai.service.stream-idle-timeout";
    private static final String CFG_JDK_BODY_IDLE_TIMEOUT = "nop.http.client.jdk.body-idle-timeout";

    private ChatServiceImpl impl;
    private JdkHttpClient client;
    private ScriptedSseServer server;

    @BeforeEach
    void setUp() {
        // 锚定生产缺省值：预算类用例只收紧本用例声明的预算键，其余预算保持缺省
        // （assign(null) 不能复原数值型缺省，tearDown 显式恢复，防跨用例泄漏）
        AppConfig.getConfigProvider().assignConfigValue(CFG_JDK_BODY_IDLE_TIMEOUT, 120_000);
        AppConfig.getConfigProvider().assignConfigValue(CFG_STREAM_TOTAL_TIMEOUT, 600_000L);
        AppConfig.getConfigProvider().assignConfigValue(CFG_STREAM_IDLE_TIMEOUT, 120_000L);

        client = new JdkHttpClient(new HttpClientConfig());
        client.start();
        impl = new ChatServiceImpl();
        impl.setHttpClient(client);
        impl.setChatLogger(new DefaultChatLogger());
    }

    @AfterEach
    void tearDown() {
        AppConfig.getConfigProvider().assignConfigValue("nop.ai.service.default-llm", null);
        AppConfig.getConfigProvider().assignConfigValue("nop.ai.llm.test-sat.base-url", null);
        AppConfig.getConfigProvider().assignConfigValue("nop.ai.llm.test-sat.api-key", null);
        AppConfig.getConfigProvider().assignConfigValue(CFG_STREAM_TOTAL_TIMEOUT, 600_000L);
        AppConfig.getConfigProvider().assignConfigValue(CFG_STREAM_IDLE_TIMEOUT, 120_000L);
        AppConfig.getConfigProvider().assignConfigValue(CFG_JDK_BODY_IDLE_TIMEOUT, 120_000);
        LlmConfigHelper.reset();
        if (client != null)
            client.stop();
        if (server != null)
            server.close();
    }

    private void pointProviderAtLocalServer() {
        AppConfig.getConfigProvider().assignConfigValue("nop.ai.service.default-llm", "test-sat");
        AppConfig.getConfigProvider().assignConfigValue("nop.ai.llm.test-sat.base-url",
                "http://127.0.0.1:" + server.port());
        AppConfig.getConfigProvider().assignConfigValue("nop.ai.llm.test-sat.api-key", "sk-test");
    }

    private static ChatRequest streamRequest() {
        ChatRequest request = ChatRequest.userPrompt("hi");
        request.setOptions(ChatOptions.builder().provider("test-sat").stream(true).build());
        return request;
    }

    /** 等待 future 终态；返回异常终态（正常完成返回 null，观察窗内无终态返回悬挂标记）。 */
    private static Throwable awaitTerminal(CompletableFuture<ChatResponse> future, long timeoutMillis)
            throws InterruptedException {
        try {
            future.get(timeoutMillis, TimeUnit.MILLISECONDS);
            return null;
        } catch (java.util.concurrent.ExecutionException e) {
            return e.getCause();
        } catch (CancellationException e) {
            return e;
        } catch (TimeoutException e) {
            return HANG_MARKER;
        } catch (CompletionException e) {
            return e.getCause();
        }
    }

    private static final Throwable HANG_MARKER = new Throwable("hang");

    // -------------------------------------------- ① 取消场景（C3-001 取消闭合）

    @Test
    void cancelTerminatesFullChainCallAsync_C3_001() throws Exception {
        server = new ScriptedSseServer();
        // headers + 首 chunk 后保持连接（无终态）——流进行中形态
        server.script(out -> out.write((
                "HTTP/1.1 200 OK\r\n"
                        + "Content-Type: text/event-stream\r\n"
                        + "Connection: close\r\n"
                        + "\r\n"
                        + "data: " + textChunk("one") + "\n\n").getBytes(StandardCharsets.UTF_8)));
        server.start();
        pointProviderAtLocalServer();

        TestStreamCancelDeadlineAndDelivery_C3_001.SettableCancelToken token =
                new TestStreamCancelDeadlineAndDelivery_C3_001.SettableCancelToken();
        CompletableFuture<ChatResponse> future = impl.callAsync(streamRequest(), token)
                .toCompletableFuture();

        assertTrue(server.awaitScriptWritten(10_000),
                "server must have delivered headers + first chunk (stream started)");
        Thread.sleep(200); // loopback 传输余量：首 chunk 已在途/已到达，流处于进行中
        assertFalse(future.isDone(), "stream must still be in progress at cancel time "
                + "(server never sends a terminal)");

        token.cancel("test-cancel");

        // 取消后 2s 观察窗：聚合 future 必须到达可区分的取消终态（修复前永久悬挂）
        Throwable terminal = awaitTerminal(future, 2_000);
        assertNotNull(terminal, "cancelled full chain must reach a terminal within the 2s "
                + "observation window (两侧同批端到端①)");
        assertTrue(terminal != HANG_MARKER, "aggregated future must not hang after cancel");
        assertTrue(terminal instanceof CancellationException,
                "cancel must surface as CancellationException, got: " + terminal);

        // reader 线程释放：取消关闭底层 body 流，服务端观测到客户端 EOF（4.3-C3-003 消费面证据）
        assertTrue(server.awaitClientEof(0, 5_000),
                "cancel must close the underlying body stream (server observed EOF) — "
                        + "the body read loop must not linger");
    }

    // ------------------------------ ② 挂死场景：双层预算（C7-001 + 4.3 body idle）

    @Test
    void aggregateTotalDeadlineTerminatesFullChainSilentSse_C7_001() throws Exception {
        AppConfig.getConfigProvider().assignConfigValue(CFG_STREAM_TOTAL_TIMEOUT, 800L);

        server = new ScriptedSseServer();
        // headers + 首 chunk 后静默（挂死传输形态——服务端不再发任何字节）
        server.script(out -> out.write((
                "HTTP/1.1 200 OK\r\n"
                        + "Content-Type: text/event-stream\r\n"
                        + "Connection: close\r\n"
                        + "\r\n"
                        + "data: " + textChunk("one") + "\n\n").getBytes(StandardCharsets.UTF_8)));
        server.start();
        pointProviderAtLocalServer();

        // 聚合层预算收紧（800ms），传输层 body idle 保持缺省 120s——观察窗内唯一可能
        // 到期的预算是聚合层流总量期限：任何终态必来自 C7-001 watchdog
        CompletableFuture<ChatResponse> future = impl.callAsync(streamRequest(), null)
                .toCompletableFuture();

        Throwable terminal = awaitTerminal(future, 5_000);
        assertNotNull(terminal, "hung full chain must terminate within the aggregate total "
                + "deadline (两侧同批端到端②-聚合层预算)");
        assertTrue(terminal != HANG_MARKER, "aggregated future must not outlive the deadline");
        assertTrue(terminal instanceof NopException, "deadline must fail with a typed error, got: "
                + terminal);
        assertEquals(ERR_AI_STREAM_TOTAL_TIMEOUT.getErrorCode(), ((NopException) terminal).getCode(),
                "aggregate total deadline must surface ERR_AI_STREAM_TOTAL_TIMEOUT");
    }

    @Test
    void transportIdleBudgetTerminatesFullChainSilentSse_C7_001() throws Exception {
        // 传输层 body idle 预算收紧（600ms），聚合层预算保持缺省（total 600s / idle 120s）
        // ——观察窗内唯一可能到期的预算是传输层 BodyIdleWatchdog：任何终态必来自 4.3 面
        AppConfig.getConfigProvider().assignConfigValue(CFG_JDK_BODY_IDLE_TIMEOUT, 600);

        server = new ScriptedSseServer();
        server.script(out -> out.write((
                "HTTP/1.1 200 OK\r\n"
                        + "Content-Type: text/event-stream\r\n"
                        + "Connection: close\r\n"
                        + "\r\n"
                        + "data: " + textChunk("one") + "\n\n").getBytes(StandardCharsets.UTF_8)));
        server.start();
        pointProviderAtLocalServer();

        CompletableFuture<ChatResponse> future = impl.callAsync(streamRequest(), null)
                .toCompletableFuture();

        Throwable terminal = awaitTerminal(future, 5_000);
        assertNotNull(terminal, "silent SSE body must be bounded by the transport-layer idle "
                + "read budget backing up the aggregate layer (两侧同批端到端②-传输层兜底)");
        assertTrue(terminal != HANG_MARKER, "full chain must not outlive the transport idle budget");
        assertTrue(terminal instanceof Exception, "transport idle close must surface as an "
                + "exceptional terminal (R21), not a silent success — got: " + terminal);
    }

    // ------------------------------ ③ 半关闭场景（C7-002 R11 纪律，全链）

    @Test
    void halfCloseCleanDisconnectFailsLoudFullChain_C7_002() throws Exception {
        server = new ScriptedSseServer();
        // 部分 chunk 后服务端干净半关闭（shutdownOutput = TCP FIN）：无 finish_reason、无 [DONE]
        server.script(new ScriptedSseServer.SseScript() {
            @Override
            public void write(OutputStream out) throws IOException {
                out.write((
                        "HTTP/1.1 200 OK\r\n"
                                + "Content-Type: text/event-stream\r\n"
                                + "Connection: close\r\n"
                                + "\r\n"
                                + "data: " + textChunk("partial one") + "\n\n"
                                + "data: " + textChunk("partial two") + "\n\n")
                        .getBytes(StandardCharsets.UTF_8));
            }

            @Override
            public boolean halfCloseAfterWrite() {
                return true;
            }
        });
        server.start();
        pointProviderAtLocalServer();

        CompletableFuture<ChatResponse> future = impl.callAsync(streamRequest(), null)
                .toCompletableFuture();

        // 传输层 EOF → publisher.clean close → 聚合 onComplete：无终止信号的截断流
        // 必须以显式异常交付（修复前 future.complete 成功交付截断内容——R11 红形态④）
        Throwable terminal = awaitTerminal(future, 5_000);
        assertNotNull(terminal, "half-closed full chain must reach a terminal");
        assertTrue(terminal != HANG_MARKER, "half-close must not hang the aggregated future");
        assertTrue(terminal instanceof NopException, "truncated stream must NOT be delivered as "
                + "success (R11 纪律), got: " + terminal);
        assertEquals(ERR_AI_STREAM_TERMINATION_SIGNAL_MISSING.getErrorCode(),
                ((NopException) terminal).getCode(),
                "half-close must surface ERR_AI_STREAM_TERMINATION_SIGNAL_MISSING");
    }

    // ------------------------------------------------------------- helpers

    static String textChunk(String content) {
        return "{\"id\":\"chunk\",\"choices\":[{\"delta\":{\"content\":\"" + content + "\"}}]}";
    }

    /**
     * 裸 socket 脚本化 SSE 服务（TestBodyPhaseCancel_C3_002 ScriptedServer 的最小变体）：
     * 按连接读请求头 → 执行响应脚本 → 可选 shutdownOutput（半关闭）→ 观测客户端 EOF。
     */
    static final class ScriptedSseServer implements Closeable {
        private final ServerSocket serverSocket;
        private final List<Socket> connections = new CopyOnWriteArrayList<>();
        private final List<CountDownLatch> eofLatches = new CopyOnWriteArrayList<>();
        private final java.util.Queue<SseScript> scripts = new ConcurrentLinkedQueue<>();
        private final CountDownLatch scriptWritten = new CountDownLatch(1);
        private volatile boolean running = true;

        ScriptedSseServer() throws IOException {
            this.serverSocket = new ServerSocket(0, 50, InetAddress.getLoopbackAddress());
        }

        int port() {
            return serverSocket.getLocalPort();
        }

        void script(SseScript responder) {
            scripts.add(responder);
        }

        void start() {
            Thread acceptThread = new Thread(this::acceptLoop, "scripted-sse-server-accept");
            acceptThread.setDaemon(true);
            acceptThread.start();
        }

        boolean awaitScriptWritten(long timeoutMillis) throws InterruptedException {
            return scriptWritten.await(timeoutMillis, TimeUnit.MILLISECONDS);
        }

        boolean awaitClientEof(int index, long timeoutMillis) throws InterruptedException {
            if (index >= eofLatches.size())
                return false;
            return eofLatches.get(index).await(timeoutMillis, TimeUnit.MILLISECONDS);
        }

        private void acceptLoop() {
            while (running) {
                try {
                    Socket socket = serverSocket.accept();
                    int index = connections.size();
                    connections.add(socket);
                    CountDownLatch eof = new CountDownLatch(1);
                    eofLatches.add(eof);
                    Thread worker = new Thread(() -> handle(socket, eof), "scripted-sse-conn-" + index);
                    worker.setDaemon(true);
                    worker.start();
                } catch (IOException e) {
                    if (running)
                        return;
                }
            }
        }

        private void handle(Socket socket, CountDownLatch eof) {
            try {
                InputStream in = socket.getInputStream();
                OutputStream out = socket.getOutputStream();
                if (readHeaders(in)) {
                    SseScript script = scripts.poll();
                    if (script != null) {
                        script.write(out);
                        out.flush();
                        scriptWritten.countDown();
                        if (script.halfCloseAfterWrite())
                            socket.shutdownOutput(); // 服务端干净半关闭（TCP FIN）
                    }
                }
                try {
                    while (in.read() != -1) {
                        // 消费请求体/后续字节直到客户端关闭
                    }
                } catch (IOException e) {
                    // 对端异常断开同样计为观测到关闭
                }
                eof.countDown();
            } catch (IOException e) {
                eof.countDown();
            }
        }

        /** 读取一个请求头块（忽略 POST 请求体——回环小体积不会阻塞客户端发送）。 */
        private static boolean readHeaders(InputStream in) throws IOException {
            StringBuilder sb = new StringBuilder();
            int b;
            while ((b = in.read()) != -1) {
                sb.append((char) b);
                if (sb.toString().endsWith("\r\n\r\n"))
                    return true;
                if (sb.length() > 64 * 1024)
                    return true;
            }
            return false;
        }

        @Override
        public void close() {
            running = false;
            try {
                serverSocket.close();
            } catch (IOException e) {
                // ignore
            }
            for (Socket socket : connections) {
                try {
                    socket.close();
                } catch (IOException e) {
                    // ignore
                }
            }
        }

        @FunctionalInterface
        interface SseScript {
            void write(OutputStream out) throws IOException;

            /** 写完响应后是否执行 shutdownOutput（服务端干净半关闭形态）。 */
            default boolean halfCloseAfterWrite() {
                return false;
            }
        }
    }
}
