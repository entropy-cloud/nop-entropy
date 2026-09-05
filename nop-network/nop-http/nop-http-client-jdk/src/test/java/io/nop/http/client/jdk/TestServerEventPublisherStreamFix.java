package io.nop.http.client.jdk;

import com.sun.net.httpserver.HttpServer;
import io.nop.http.api.client.HttpClientConfig;
import io.nop.http.api.client.HttpRequest;
import io.nop.http.api.client.IServerEventResponse;
import org.junit.jupiter.api.Test;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestServerEventPublisherStreamFix {

    @Test
    public void testUtf8AndCrlfEvents() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/sse", exchange -> {
            exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
            exchange.sendResponseHeaders(200, 0);
            OutputStream os = exchange.getResponseBody();
            // CRLF 行结尾 + UTF-8 多字节内容
            os.write("data: 你好\r\n\r\n".getBytes(StandardCharsets.UTF_8));
            os.write("data: done-marker\n\n".getBytes(StandardCharsets.UTF_8));
            os.flush();
            os.close();
        });
        server.start();
        try {
            JdkHttpClient client = new JdkHttpClient(new HttpClientConfig());
            client.start();
            try {
                List<String> events = new CopyOnWriteArrayList<>();
                CountDownLatch firstEvent = new CountDownLatch(1);

                client.fetchServerEventFlow(
                                HttpRequest.get("http://127.0.0.1:" + server.getAddress().getPort() + "/sse"), null)
                        .subscribe(new Flow.Subscriber<>() {
                            private Flow.Subscription subscription;

                            @Override
                            public void onSubscribe(Flow.Subscription subscription) {
                                this.subscription = subscription;
                                subscription.request(1);
                            }

                            @Override
                            public void onNext(IServerEventResponse item) {
                                events.add(item.getData());
                                firstEvent.countDown();
                                subscription.request(1);
                            }

                            @Override
                            public void onError(Throwable throwable) {
                            }

                            @Override
                            public void onComplete() {
                            }
                        });

                assertTrue(firstEvent.await(10, TimeUnit.SECONDS), "first event must arrive");
                // CRLF 流上第一个事件的 data 不应带尾部 \r，UTF-8 内容不应乱码
                assertEquals("你好", events.get(0));
            } finally {
                client.stop();
            }
        } finally {
            server.stop(0);
        }
    }

    @Test
    public void testNoEventsAfterCancel() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/sse", exchange -> {
            exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
            exchange.sendResponseHeaders(200, 0);
            OutputStream os = exchange.getResponseBody();
            for (int i = 0; i < 200; i++) {
                os.write(("data: event-" + i + "\n\n").getBytes(StandardCharsets.UTF_8));
                os.flush();
                try {
                    Thread.sleep(20);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            os.close();
        });
        server.start();
        try {
            JdkHttpClient client = new JdkHttpClient(new HttpClientConfig());
            client.start();
            try {
                List<String> events = new CopyOnWriteArrayList<>();
                CountDownLatch firstEvent = new CountDownLatch(1);
                AtomicReference<Flow.Subscription> subRef = new AtomicReference<>();

                client.fetchServerEventFlow(
                                HttpRequest.get("http://127.0.0.1:" + server.getAddress().getPort() + "/sse"), null)
                        .subscribe(new Flow.Subscriber<>() {
                            @Override
                            public void onSubscribe(Flow.Subscription subscription) {
                                subRef.set(subscription);
                                subscription.request(Long.MAX_VALUE);
                            }

                            @Override
                            public void onNext(IServerEventResponse item) {
                                events.add(item.getData());
                                firstEvent.countDown();
                            }

                            @Override
                            public void onError(Throwable throwable) {
                            }

                            @Override
                            public void onComplete() {
                            }
                        });

                assertTrue(firstEvent.await(10, TimeUnit.SECONDS));
                Thread.sleep(200);
                subRef.get().cancel();
                int sizeAfterCancel = events.size();
                Thread.sleep(600);
                // 取消后事件数不应明显增长（parseEvents 在取消后必须停止派发）
                assertTrue(events.size() <= sizeAfterCancel + 1,
                        "events must stop after cancel: before=" + sizeAfterCancel + " after=" + events.size());
            } finally {
                client.stop();
            }
        } finally {
            server.stop(0);
        }
    }
}
