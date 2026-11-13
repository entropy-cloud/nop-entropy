package io.nop.http.client.jdk;

import com.sun.net.httpserver.HttpServer;
import io.nop.api.core.config.AppConfig;
import io.nop.api.core.util.ICancelToken;
import io.nop.http.api.client.HttpClientConfig;
import io.nop.http.api.client.HttpRequest;
import io.nop.http.api.client.IHttpResponse;
import io.nop.http.api.client.IServerEventResponse;
import io.nop.http.api.support.DefaultHttpOutputFile;
import io.nop.http.api.utils.FileTransferHelper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CRA 4.3-C3-002 族（plan 2505 Phase 1，含 C3-003/C6-002/CL-026 同 diff）：
 * JDK 客户端响应体阶段（body phase）的取消响应点、idle 读超时与生命周期出口收口。
 *
 * <p>红形态②（传输层读红）：取消/静默后读线程仍阻塞在 read/readLine、sink 持续增长（幽灵下载签名）。
 * 红形态③（遗弃红）：416 分支与 SSE cancel-at-entry 分支后响应体流必须关闭可观测
 * （raw socket 对端 EOF 作为关闭信号，接线验证 rule 23——真实 JdkHttpClient/ServerEventPublisher
 * 装配链上断言取消语义运行时生效）。
 */
public class TestBodyPhaseCancel_C3_002 {

    private static final String CFG_BODY_IDLE_TIMEOUT = "nop.http.client.jdk.body-idle-timeout";

    private JdkHttpClient client;
    private HttpServer httpServer;
    private ScriptedServer rawServer;
    private volatile CountDownLatch handlerDone;

    @BeforeEach
    public void setUp() {
        // 锚定生产缺省值：取消响应类用例（c/e/f）不受其他用例遗留 idle 配置影响，
        // 关闭只能来自 cleanup/分支关流路径（真实接线验证）；idle 用例内再收紧为 800ms
        AppConfig.getConfigProvider().assignConfigValue(CFG_BODY_IDLE_TIMEOUT, 120_000);
        client = new JdkHttpClient(new HttpClientConfig());
        client.start();
    }

    @AfterEach
    public void tearDown() {
        // 先释放 handler（server.stop 会等待 handler 返回），再关闭
        if (handlerDone != null)
            handlerDone.countDown();
        if (client != null)
            client.stop();
        if (httpServer != null)
            httpServer.stop(0);
        if (rawServer != null)
            rawServer.close();
        // 恢复生产缺省值（assignConfigValue(null) 不能复原缺省，会向同 JVM 后续用例泄漏）
        AppConfig.getConfigProvider().assignConfigValue(CFG_BODY_IDLE_TIMEOUT, 120_000);
    }

    // --------------------------------------------------------------- token

    @FunctionalInterface
    interface Script {
        void write(OutputStream out) throws IOException;
    }

    static class SettableCancelToken implements ICancelToken {
        private final List<Consumer<String>> tasks = new CopyOnWriteArrayList<>();
        private volatile boolean cancelled;
        private volatile String reason;

        @Override
        public boolean isCancelled() {
            return cancelled;
        }

        @Override
        public String getCancelReason() {
            return reason;
        }

        @Override
        public void appendOnCancel(Consumer<String> task) {
            tasks.add(task);
        }

        @Override
        public void removeOnCancel(Consumer<String> task) {
            tasks.remove(task);
        }

        void cancel(String reason) {
            this.reason = reason;
            this.cancelled = true;
            for (Consumer<String> task : tasks) {
                task.accept(reason);
            }
        }
    }

    // ------------------------------------------------- ghost download (a)

    @Test
    public void downloadCancelStopsGhostBodyRead_C3_002() throws Exception {
        handlerDone = new CountDownLatch(1);
        httpServer = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        httpServer.createContext("/big", exchange -> {
            exchange.getResponseHeaders().set("Content-Type", "application/octet-stream");
            exchange.sendResponseHeaders(200, 0);
            try (OutputStream os = exchange.getResponseBody()) {
                while (!awaitQuietly(handlerDone, 50)) {
                    os.write(new byte[8192]);
                    os.flush();
                }
            } catch (IOException e) {
                // 客户端断开时服务端写失败，属预期
            }
        });
        httpServer.start();
        String downloadUrl = "http://127.0.0.1:" + httpServer.getAddress().getPort() + "/big";

        File target = File.createTempFile("jdk-ghost-dl", ".bin");
        target.deleteOnExit();
        assertTrue(target.delete(), "target must start absent so sink creates fresh");

        SettableCancelToken token = new SettableCancelToken();
        CompletableFuture<IHttpResponse> promise = client.downloadAsync(
                HttpRequest.get(downloadUrl), DefaultHttpOutputFile.create(target), downloadOptions(), token)
                .toCompletableFuture();

        File part = FileTransferHelper.partFileOf(target);
        long deadline = System.currentTimeMillis() + 10_000;
        while (partSize(part) == 0 && System.currentTimeMillis() < deadline)
            Thread.sleep(50);
        assertTrue(partSize(part) > 0, "download must start writing the part file");

        token.cancel("test-cancel");

        // 取消后读循环必须有界退出（幽灵下载消除）：promise 以取消终态完成
        Throwable terminal = awaitTerminal(promise, 3_000);
        assertNotNull(terminal, "cancelled download must terminate exceptionally within the bounded window "
                + "(4.3-C3-002 ghost download), terminal=" + terminal);
        long sizeAfterCancel = partSize(part);
        Thread.sleep(1000);
        assertEquals(sizeAfterCancel, partSize(part),
                "sink must stop growing after cancel (ghost download signature must disappear)");
        handlerDone.countDown();
    }

    // ------------------------------------------- silent download (CL-026)

    @Test
    public void downloadIdleTimeoutTerminatesSilentBody_CL_026() throws Exception {
        AppConfig.getConfigProvider().assignConfigValue(CFG_BODY_IDLE_TIMEOUT, 800);

        handlerDone = new CountDownLatch(1);
        httpServer = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        httpServer.createContext("/silent", exchange -> {
            exchange.getResponseHeaders().set("Content-Type", "application/octet-stream");
            exchange.sendResponseHeaders(200, 0);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(new byte[64]);
                os.flush();
                // 服务端中途停滞：不再发送任何字节（CL-026 静默面）
                handlerDone.await(30, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        httpServer.start();
        String downloadUrl = "http://127.0.0.1:" + httpServer.getAddress().getPort() + "/silent";

        File target = File.createTempFile("jdk-silent-dl", ".bin");
        target.deleteOnExit();
        assertTrue(target.delete());

        CompletableFuture<IHttpResponse> promise = client.downloadAsync(
                HttpRequest.get(downloadUrl), DefaultHttpOutputFile.create(target), downloadOptions(), null)
                .toCompletableFuture();

        Throwable terminal = awaitTerminal(promise, 5_000);
        assertNotNull(terminal,
                "silent body read must be bounded by the idle read budget (4.3-CL-026), no permanent hang");
        handlerDone.countDown();
    }

    // -------------------------------------------- SSE cancel closes body

    @Test
    public void sseCancelClosesUnderlyingStream_C3_003() throws Exception {
        rawServer = new ScriptedServer();
        rawServer.script(out -> out.write((
                "HTTP/1.1 200 OK\r\n"
                        + "Content-Type: text/event-stream\r\n"
                        + "Connection: close\r\n"
                        + "\r\n"
                        + "data: first\n\n").getBytes(StandardCharsets.UTF_8)));
        rawServer.start();
        String sseUrl = "http://127.0.0.1:" + rawServer.port() + "/sse";

        SettableCancelToken token = new SettableCancelToken();
        CountDownLatch firstEvent = new CountDownLatch(1);
        client.fetchServerEventFlow(HttpRequest.get(sseUrl), token).subscribe(new Flow.Subscriber<>() {
            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                subscription.request(1);
            }

            @Override
            public void onNext(IServerEventResponse item) {
                firstEvent.countDown();
            }

            @Override
            public void onError(Throwable throwable) {
            }

            @Override
            public void onComplete() {
            }
        });

        assertTrue(firstEvent.await(10, TimeUnit.SECONDS), "first SSE event must arrive");

        // 取消后底层流必须被显式关闭（对端观测 EOF），读线程不再滞留 readLine
        token.cancel("test-cancel");
        boolean closed = rawServer.awaitClientEof(0, 15_000);
        assertTrue(closed, "cancel must close the underlying body stream (server observed EOF) "
                + "within the bounded window (4.3-C3-003)");
    }

    // -------------------------------------------- SSE idle timeout (d)

    @Test
    public void sseIdleTimeoutTerminatesSilentStream_C3_002() throws Exception {
        AppConfig.getConfigProvider().assignConfigValue(CFG_BODY_IDLE_TIMEOUT, 800);

        handlerDone = new CountDownLatch(1);
        httpServer = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        httpServer.createContext("/sse-silent", exchange -> {
            exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
            exchange.sendResponseHeaders(200, 0);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write("data: first\n\n".getBytes(StandardCharsets.UTF_8));
                os.flush();
                // SSE 静默停滞：不再发送事件
                handlerDone.await(30, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        httpServer.start();
        String sseUrl = "http://127.0.0.1:" + httpServer.getAddress().getPort() + "/sse-silent";

        CountDownLatch firstEvent = new CountDownLatch(1);
        AtomicReference<Throwable> errorRef = new AtomicReference<>();
        CountDownLatch terminal = new CountDownLatch(1);
        client.fetchServerEventFlow(HttpRequest.get(sseUrl), null)
                .subscribe(new Flow.Subscriber<>() {
                    @Override
                    public void onSubscribe(Flow.Subscription subscription) {
                        subscription.request(Long.MAX_VALUE);
                    }

                    @Override
                    public void onNext(IServerEventResponse item) {
                        firstEvent.countDown();
                    }

                    @Override
                    public void onError(Throwable throwable) {
                        errorRef.set(throwable);
                        terminal.countDown();
                    }

                    @Override
                    public void onComplete() {
                        terminal.countDown();
                    }
                });

        assertTrue(firstEvent.await(10, TimeUnit.SECONDS), "first SSE event must arrive");
        assertTrue(terminal.await(5_000, TimeUnit.MILLISECONDS),
                "silent SSE stream must terminate within the idle read budget (4.3-C3-003/CL-026)");
        assertNotNull(errorRef.get(), "idle timeout must surface as onError, not a silent end");
        handlerDone.countDown();
    }

    // --------------------------------------------- 416 branch close (e)

    @Test
    public void retry416BranchClosesAbandonedBody_C3_002() throws Exception {
        rawServer = new ScriptedServer();
        // 416 响应不带 Connection: close（保持池化语义），且 Content-Length 大于实际
        // 发送字节——交换停留在「body 在途」形态：不显式关闭时连接停留于「响应体未消费」
        // 状态（对端无 EOF），分支关闭才中止交换并产生确定性 EOF 信号
        rawServer.script(out -> out.write((
                "HTTP/1.1 416 Requested Range Not Satisfiable\r\n"
                        + "Content-Type: text/plain\r\n"
                        + "Content-Length: 100\r\n"
                        + "\r\n"
                        + "stale body").getBytes(StandardCharsets.UTF_8)));
        rawServer.script(out -> out.write((
                "HTTP/1.1 200 OK\r\n"
                        + "Content-Type: application/octet-stream\r\n"
                        + "Content-Length: 10\r\n"
                        + "\r\n"
                        + "0123456789").getBytes(StandardCharsets.UTF_8)));
        rawServer.start();
        String downloadUrl = "http://127.0.0.1:" + rawServer.port() + "/dl";

        File target = File.createTempFile("jdk-416-dl", ".bin");
        target.deleteOnExit();
        assertTrue(target.delete());
        // 造断点续传状态：.part 比远端新 → 首请求带 Range → 服务端回 416
        Files.write(FileTransferHelper.partFileOf(target).toPath(),
                "0123".getBytes(StandardCharsets.UTF_8));

        // 无 checksum 期望时平台契约不 rename（part 保底续传）——本用例显式提供期望摘要
        io.nop.http.api.client.DownloadOptions options = downloadOptions();
        options.setExpectedSha256(sha256Hex("0123456789".getBytes(StandardCharsets.UTF_8)));
        IHttpResponse response = client.downloadAsync(HttpRequest.get(downloadUrl),
                DefaultHttpOutputFile.create(target), options, null)
                .toCompletableFuture().get(20, TimeUnit.SECONDS);
        assertEquals(200, response.getHttpStatus());
        assertEquals("0123456789",
                new String(Files.readAllBytes(target.toPath()), StandardCharsets.UTF_8),
                "retry-from-scratch must complete the download");

        // 416 分支 return 前必须关闭遗弃的响应体（对端观测 EOF）——C6-002①
        assertTrue(rawServer.awaitClientEof(0, 3_000),
                "416 retry branch must close the abandoned response body (server observed EOF) "
                        + "within the bounded window (4.3-C6-002)");
    }

    // ------------------------------------ SSE cancel-at-entry close (f)

    @Test
    public void sseCancelAtEntryClosesAbandonedBody_C3_002() throws Exception {
        rawServer = new ScriptedServer();
        // Content-Length 大于实际发送字节：交换停留在「body 未收完」的在途形态——
        // body 关闭（无论经 cleanup 还是 cancel-at-entry 分支）都会中止交换并产生对端 EOF
        rawServer.script(out -> out.write((
                "HTTP/1.1 200 OK\r\n"
                        + "Content-Type: text/event-stream\r\n"
                        + "Content-Length: 100\r\n"
                        + "Connection: close\r\n"
                        + "\r\n"
                        + "data: first\n\n").getBytes(StandardCharsets.UTF_8)));
        rawServer.start();
        String sseUrl = "http://127.0.0.1:" + rawServer.port() + "/sse";

        // executor 任务被捕获而不执行 → 构造「响应已到达、parse 任务未启动」的确定性窗口
        AtomicReference<Runnable> pendingTask = new AtomicReference<>();
        SettableCancelToken cancelToken = new SettableCancelToken();
        ServerEventPublisher publisher = new ServerEventPublisher(
                HttpClient.newHttpClient(),
                java.net.http.HttpRequest.newBuilder().GET().uri(URI.create(sseUrl)).build(),
                cancelToken) {
            @Override
            protected Executor executor() {
                return pendingTask::set;
            }
        };

        publisher.subscribe(new Flow.Subscriber<>() {
            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                subscription.request(1);
            }

            @Override
            public void onNext(IServerEventResponse item) {
            }

            @Override
            public void onError(Throwable throwable) {
            }

            @Override
            public void onComplete() {
            }
        });

        long deadline = System.currentTimeMillis() + 10_000;
        while (pendingTask.get() == null && System.currentTimeMillis() < deadline)
            Thread.sleep(20);
        assertNotNull(pendingTask.get(),
                "response must arrive and the parse task must be captured (cancel-at-entry window)");

        cancelToken.cancel("test-cancel");
        // 在独立线程上执行被捕获的 parse 任务（修复前该任务会无限滞留 readLine，不能跑在主线程）
        Thread parseTask = new Thread(pendingTask.get(), "test-cancel-at-entry-task");
        parseTask.setDaemon(true);
        parseTask.start();

        assertTrue(rawServer.awaitClientEof(0, 15_000),
                "cancel-at-entry branch must close the abandoned response body (server observed EOF) "
                        + "within the bounded window (4.3-C6-002)");
    }

    // ------------------------------------------------------------- helpers

    private static String sha256Hex(byte[] content) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(content);
            StringBuilder sb = new StringBuilder();
            for (byte b : hashed)
                sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static io.nop.http.api.client.DownloadOptions downloadOptions() {
        io.nop.http.api.client.DownloadOptions options = new io.nop.http.api.client.DownloadOptions();
        options.setFetchSidecarChecksum(false);
        return options;
    }

    private static boolean awaitQuietly(CountDownLatch latch, long millis) {
        try {
            return latch.await(millis, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return true;
        }
    }

    private static long partSize(File part) {
        return part != null && part.exists() ? part.length() : 0L;
    }

    /**
     * 等待 promise 以任意终态（正常/异常/取消）完成，返回异常终态（观察窗内无终态返回 null = 悬挂 RED 信号）。
     */
    private static Throwable awaitTerminal(CompletableFuture<IHttpResponse> promise, long timeoutMillis)
            throws InterruptedException {
        try {
            promise.get(timeoutMillis, TimeUnit.MILLISECONDS);
            return null;
        } catch (ExecutionException e) {
            return e.getCause();
        } catch (CancellationException e) {
            return e;
        } catch (TimeoutException e) {
            return null;
        }
    }

    /**
     * 裸 socket 脚本化 HTTP 服务：按连接顺序执行响应脚本，逐连接观测客户端关闭（EOF）。
     */
    static final class ScriptedServer implements Closeable {
        private final ServerSocket serverSocket;
        private final List<Socket> connections = new CopyOnWriteArrayList<>();
        private final List<CountDownLatch> eofLatches = new CopyOnWriteArrayList<>();
        private final java.util.Queue<Script> scripts = new ConcurrentLinkedQueue<>();
        private volatile boolean running = true;

        ScriptedServer() throws IOException {
            this.serverSocket = new ServerSocket(0, 50, InetAddress.getLoopbackAddress());
        }

        int port() {
            return serverSocket.getLocalPort();
        }

        void script(Script responder) {
            scripts.add(responder);
        }

        void start() {
            Thread acceptThread = new Thread(this::acceptLoop, "scripted-server-accept");
            acceptThread.setDaemon(true);
            acceptThread.start();
        }

        private void acceptLoop() {
            while (running) {
                try {
                    Socket socket = serverSocket.accept();
                    int index = connections.size();
                    connections.add(socket);
                    CountDownLatch eof = new CountDownLatch(1);
                    eofLatches.add(eof);
                    Thread worker = new Thread(() -> handle(socket, eof), "scripted-server-conn-" + index);
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
                // 同一连接可承载多个交换（客户端池化复用），按脚本顺序依次响应
                while (true) {
                    if (!readHeaders(in))
                        break; // EOF：客户端已关闭
                    Script script = scripts.poll();
                    if (script == null)
                        break; // 无脚本：转入 EOF 观察
                    script.write(out);
                    out.flush();
                }
                try {
                    while (in.read() != -1) {
                        // 消费后续字节直到客户端关闭
                    }
                } catch (IOException e) {
                    // 对端异常断开同样计为观测到关闭
                }
                eof.countDown();
            } catch (IOException e) {
                // 头读取失败：连接未能建立到脚本阶段
                eof.countDown();
            }
        }

        /** 读取一个请求头块；读到流尾（客户端关闭）返回 false。 */
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

        boolean awaitClientEof(int index, long timeoutMillis) throws InterruptedException {
            if (index >= eofLatches.size())
                return false;
            return eofLatches.get(index).await(timeoutMillis, TimeUnit.MILLISECONDS);
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
    }
}
