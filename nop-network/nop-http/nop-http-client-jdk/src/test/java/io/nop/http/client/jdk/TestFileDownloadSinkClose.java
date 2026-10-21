/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.http.client.jdk;

import com.sun.net.httpserver.HttpServer;
import io.nop.http.api.client.DownloadOptions;
import io.nop.http.api.client.HttpClientConfig;
import io.nop.http.api.client.HttpRequest;
import io.nop.http.api.client.IHttpResponse;
import io.nop.http.api.utils.FileTransferHelper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIf;

import java.io.File;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 回归（CRA WI 4.3 C6-001，P0）：JDK/OkHttp 下载成功路径从不调用 FileDownloadSink.close()——
 * resume=true（缺省）下每次成功下载泄漏一个文件句柄；Windows 上 Files.move 因句柄占用确定性失败。
 * 修复：processDownloadResponse / OkHttp onResponse 成功路径在 verifyAndRename 后补 sink.close()。
 *
 * <p>回退必红判定：修复前每次成功下载泄漏 1 个 fd，本测试连发 {@link #DOWNLOADS} 次后 fd 增量
 * 超 {@link #MAX_FD_GROWTH} 断言失败（Unix/macOS 上 com.sun.management.OperatingSystemMXBean
 * 可观测；修复后句柄关闭，fd 增量回落到连接池常驻水平）。</p>
 */
@Timeout(120)
@EnabledIf("io.nop.http.client.jdk.TestFileDownloadSinkClose#openFileDescriptorCountAvailable")
public class TestFileDownloadSinkClose {

    static final int DOWNLOADS = 40;
    /** 修复前：+DOWNLOADS 个泄漏 fd 必然超限；修复后：仅连接池常驻 fd 浮动。 */
    static final int MAX_FD_GROWTH = 10;

    private HttpServer server;
    private JdkHttpClient client;

    static final byte[] CONTENT = new byte[64 * 1024];

    String contentSha256;

    @BeforeEach
    void setUp() throws IOException {
        for (int i = 0; i < CONTENT.length; i++)
            CONTENT[i] = (byte) ('a' + (i % 26));
        contentSha256 = java.util.HexFormat.of().formatHex(
                FileTransferHelper.newDigest("SHA-256").digest(CONTENT));

        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        client = new JdkHttpClient(new HttpClientConfig());
        client.start();
        server.createContext("/file", exchange -> {
            exchange.getResponseHeaders().set("Content-Length", String.valueOf(CONTENT.length));
            exchange.sendResponseHeaders(200, CONTENT.length);
            exchange.getResponseBody().write(CONTENT);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void tearDown() {
        client.stop();
        server.stop(0);
    }

    static boolean openFileDescriptorCountAvailable() {
        try {
            Class.forName("com.sun.management.UnixOperatingSystemMXBean");
            return ManagementFactory.getOperatingSystemMXBean()
                    instanceof com.sun.management.UnixOperatingSystemMXBean;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    static long openFdCount() {
        return ((com.sun.management.UnixOperatingSystemMXBean) ManagementFactory
                .getOperatingSystemMXBean()).getOpenFileDescriptorCount();
    }

    private void downloadOnce(int i) throws Exception {
        File target = Files.createTempFile("c6p0-" + i, ".bin").toFile();
        try {
            HttpRequest request = HttpRequest
                    .get("http://127.0.0.1:" + server.getAddress().getPort() + "/file");
            DownloadOptions options = new DownloadOptions();
            options.setExpectedSha256(contentSha256);
            CompletionStage<IHttpResponse> stage = client.downloadAsync(request,
                    new io.nop.http.api.support.DefaultHttpOutputFile(target),
                    options, null);
            IHttpResponse response = stage.toCompletableFuture().get(30, TimeUnit.SECONDS);
            assertEquals(200, response.getHttpStatus());
            assertEquals(CONTENT.length, Files.size(target.toPath()));
        } finally {
            Files.deleteIfExists(target.toPath());
        }
    }

    @Test
    public void testSuccessfulDownloadsDoNotLeakFileDescriptors() throws Exception {
        // 预热：建立连接池常驻 fd（避免把池建立计入泄漏增量）
        for (int i = 0; i < 3; i++)
            downloadOnce(-i);

        long before = openFdCount();
        for (int i = 0; i < DOWNLOADS; i++)
            downloadOnce(i);
        // 等待 reactor/executor 完全归还
        for (int i = 0; i < 50 && openFdCount() - before > MAX_FD_GROWTH; i++)
            Thread.sleep(100);

        long growth = openFdCount() - before;
        assertTrue(growth <= MAX_FD_GROWTH,
                "successful downloads must not leak file handles (C6-001): leaked=" + growth
                        + " over " + DOWNLOADS + " downloads");
    }
}
