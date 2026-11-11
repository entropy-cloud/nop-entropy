package io.nop.http.client.jdk;

import com.sun.net.httpserver.HttpServer;
import io.nop.http.api.client.HttpClientConfig;
import io.nop.http.api.client.HttpRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.OutputStream;
import java.lang.reflect.Field;
import java.net.InetSocketAddress;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 4.3-MA-16+C6-025（plan 2560 裁定 I / C10-004① 部分面）：stop 收敛断言。
 *
 * <ul>
 *   <li>缺陷形态：{@code JdkHttpClient.stop} 只销毁自建 executor，自持 JDK
 *       {@code HttpClient}（selector/连接池线程）从不 close——stop 后资源滞留。</li>
 *   <li>修复形态：stop 补 {@code HttpClient.close()}（JDK 21+ AutoCloseable），
 *       client 字段清空，dispatcher 线程计数回落。</li>
 * </ul>
 */
@Timeout(60)
public class TestJdkHttpClientStop {

    private static Object clientField(JdkHttpClient client) throws Exception {
        Field f = JdkHttpClient.class.getDeclaredField("client");
        f.setAccessible(true);
        return f.get(client);
    }

    private static int httpThreads() {
        int n = 0;
        for (Thread t : Thread.getAllStackTraces().keySet()) {
            String name = t.getName();
            if (name.startsWith("HTTP-") || name.contains("HttpClient"))
                n++;
        }
        return n;
    }

    /**
     * close 调用断言 + 线程计数回落：经本地 HttpServer 触发一次真实请求确保 JDK
     * HttpClient 的 dispatcher 线程已生成，stop 后 client 字段必须清空且线程在宽限
     * 窗口内退出（缺陷形态下 close 缺失、线程计数永久滞留）。
     */
    @Test
    public void stopClosesHttpClientAndReleasesThreads() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            byte[] body = "ok".getBytes();
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        });
        server.start();
        try {
            int baseline = httpThreads();
            HttpClientConfig config = new HttpClientConfig();
            JdkHttpClient client = new JdkHttpClient(config);
            client.start();
            assertNotNull(clientField(client), "start must build the JDK HttpClient");

            HttpRequest request = HttpRequest.get("http://127.0.0.1:" + server.getAddress().getPort() + "/x");
            client.fetchAsync(request, null).toCompletableFuture().get(10, TimeUnit.SECONDS);

            int withClient = httpThreads();
            assertTrue(withClient > baseline,
                    "precondition: JDK HttpClient threads must exist after a real request"
                            + " (baseline=" + baseline + ", now=" + withClient + ")");

            client.stop();

            assertNull(clientField(client),
                    "MA-16/裁定I: stop must close the JDK HttpClient and release it (defect: client never closed)");

            // dispatcher 线程回落宽限窗口（close 同步返回，线程退出略有延迟）
            boolean released = false;
            for (int i = 0; i < 100; i++) {
                if (httpThreads() <= baseline) {
                    released = true;
                    break;
                }
                Thread.sleep(50);
            }
            assertTrue(released,
                    "MA-16/裁定I: JDK HttpClient dispatcher threads must exit after stop"
                            + " (threads stuck at " + httpThreads() + ", baseline=" + baseline + ")");
        } finally {
            server.stop(0);
        }
    }
}
