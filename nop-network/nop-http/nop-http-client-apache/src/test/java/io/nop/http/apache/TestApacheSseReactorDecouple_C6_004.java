package io.nop.http.apache;

import io.nop.api.core.util.ICancelToken;
import io.nop.http.api.client.HttpClientConfig;
import io.nop.http.api.client.HttpRequest;
import io.nop.http.api.client.IHttpResponse;
import io.nop.http.api.client.IServerEventResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CRA 4.3-C6-004 面②/④（plan 2505 Phase 1）：Apache SSE 反压不得以 HC5 I/O reactor
 * 线程的阻塞 wait 实现——停滞订阅者（有界 demand 不 request 也不 cancel）不得挂起
 * 同 reactor 上其他连接的处理；新增事件派发承载线程必须为 daemon 形态（R06 族兜底半边）。
 *
 * <p>红形态⑤：demand 挂起 = reactor/承载线程滞留 waitForDemand 超预算窗。本用例以
 * 「停滞 SSE 订阅存在时，同 reactor 的普通请求仍有界完成」为 reactor 存活信号
 * （接线验证 rule 23——真实 ApacheHttpClient 装配链上断言）。
 */
public class TestApacheSseReactorDecouple_C6_004 {

    private ApacheHttpClient client;
    private ScriptedServer rawServer;

    @FunctionalInterface
    interface Script {
        void write(OutputStream out) throws IOException;
    }

    @BeforeEach
    public void setUp() {
        // 单 reactor 线程（ioThreadCount=1）：使「停滞订阅者挂起共享 reactor」的缺陷形态可确定性观测
        HttpClientConfig config = new HttpClientConfig();
        config.setIoThreadCount(1);
        client = new ApacheHttpClient(config);
        client.start();
    }

    @AfterEach
    public void tearDown() {
        if (client != null)
            client.stop();
        if (rawServer != null)
            rawServer.close();
    }

    @Test
    public void stalledSseSubscriberDoesNotBlockReactor_C6_004() throws Exception {
        rawServer = new ScriptedServer();
        // SSE 连接：一次性发送多个事件（第 1 个事件消耗 demand=1，第 2 个事件触发 demand 等待）
        rawServer.script(out -> out.write((
                "HTTP/1.1 200 OK\r\n"
                        + "Content-Type: text/event-stream\r\n"
                        + "Connection: close\r\n"
                        + "\r\n"
                        + "data: one\n\n"
                        + "data: two\n\n"
                        + "data: three\n\n").getBytes(StandardCharsets.UTF_8)));
        // 普通请求：小体积 200 响应（reactor 存活信号）
        rawServer.script(out -> out.write((
                "HTTP/1.1 200 OK\r\n"
                        + "Content-Type: text/plain\r\n"
                        + "Content-Length: 2\r\n"
                        + "\r\n"
                        + "ok").getBytes(StandardCharsets.UTF_8)));
        rawServer.start();
        String base = "http://127.0.0.1:" + rawServer.port();

        CountDownLatch firstEvent = new CountDownLatch(1);
        client.fetchServerEventFlow(HttpRequest.get(base + "/sse"), null)
                .subscribe(new Flow.Subscriber<>() {
                    @Override
                    public void onSubscribe(Flow.Subscription subscription) {
                        // 有界 demand：消费 1 个事件后停滞（不 request 也不 cancel）
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

        // 停滞订阅存在时，同 reactor 的普通请求必须有界完成（reactor 未被内联 wait 挂起）
        CompletableFuture<IHttpResponse> probe = client
                .fetchAsync(HttpRequest.get(base + "/probe"), null).toCompletableFuture();
        IHttpResponse response = probe.get(5, TimeUnit.SECONDS);
        assertTrue(response.getHttpStatus() == 200,
                "probe request must complete while an SSE subscriber stalls (reactor must not block on demand wait)");
    }

    @Test
    public void sseDispatchCarrierIsDaemon_C6_004() throws Exception {
        rawServer = new ScriptedServer();
        rawServer.script(out -> out.write((
                "HTTP/1.1 200 OK\r\n"
                        + "Content-Type: text/event-stream\r\n"
                        + "Connection: close\r\n"
                        + "\r\n"
                        + "data: one\n\n").getBytes(StandardCharsets.UTF_8)));
        rawServer.start();
        String base = "http://127.0.0.1:" + rawServer.port();

        CountDownLatch firstEvent = new CountDownLatch(1);
        client.fetchServerEventFlow(HttpRequest.get(base + "/sse"), null)
                .subscribe(new Flow.Subscriber<>() {
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

        // 派发承载线程必须存在且为 daemon（挂死不阻断 JVM 退出，R06 族口径）
        long deadline = System.currentTimeMillis() + 5_000;
        Thread carrier = null;
        while (carrier == null && System.currentTimeMillis() < deadline) {
            for (Thread t : Thread.getAllStackTraces().keySet()) {
                if (t.getName().startsWith("nop-sse-dispatch")) {
                    carrier = t;
                    break;
                }
            }
            if (carrier == null)
                Thread.sleep(50);
        }
        assertTrue(carrier != null, "apache SSE dispatch carrier thread must exist");
        assertTrue(carrier.isDaemon(), "apache SSE dispatch carrier thread must be daemon (4.3-C6-004 face④)");
    }

    /**
     * 裸 socket 脚本化 HTTP 服务：按连接顺序执行响应脚本。
     */
    static final class ScriptedServer implements Closeable {
        private final ServerSocket serverSocket;
        private final List<Socket> connections = new CopyOnWriteArrayList<>();
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
                    connections.add(socket);
                    Thread worker = new Thread(() -> handle(socket), "scripted-server-conn");
                    worker.setDaemon(true);
                    worker.start();
                } catch (IOException e) {
                    if (running)
                        return;
                }
            }
        }

        private void handle(Socket socket) {
            try {
                readHeaders(socket.getInputStream());
                Script script = scripts.poll();
                if (script != null) {
                    script.write(socket.getOutputStream());
                    socket.getOutputStream().flush();
                }
                // 保持连接打开（SSE 面由脚本决定后续行为；EOF 观测不需要）
            } catch (IOException e) {
                // 头读取失败
            }
        }

        private static void readHeaders(InputStream in) throws IOException {
            StringBuilder sb = new StringBuilder();
            int b;
            while ((b = in.read()) != -1) {
                sb.append((char) b);
                if (sb.toString().endsWith("\r\n\r\n"))
                    break;
                if (sb.length() > 64 * 1024)
                    break;
            }
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
