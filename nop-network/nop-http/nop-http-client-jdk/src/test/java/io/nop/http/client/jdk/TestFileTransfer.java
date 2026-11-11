package io.nop.http.client.jdk;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.nop.api.core.exceptions.NopException;
import io.nop.http.api.client.DownloadOptions;
import io.nop.http.api.client.HttpClientConfig;
import io.nop.http.api.client.HttpRequest;
import io.nop.http.api.client.IHttpResponse;
import io.nop.http.api.client.UploadMode;
import io.nop.http.api.client.UploadOptions;
import io.nop.http.api.support.DefaultHttpInputFile;
import io.nop.http.api.support.DefaultHttpOutputFile;
import io.nop.http.api.utils.FileTransferHelper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIf;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * JDK 客户端文件传输回归：与 Apache 实现行为等价（同协议矩阵）
 */
@Timeout(60)
@EnabledIf("io.nop.http.client.jdk.HttpFileTransferTestCondition#isFileTransferReliableOnThisOS")
public class TestFileTransfer {

    private HttpServer server;
    private JdkHttpClient client;

    static final byte[] CONTENT = ("jdk file transfer 内容\n" + "y".repeat(50_000)).getBytes(StandardCharsets.UTF_8);
    static final String CONTENT_SHA256 = java.util.HexFormat.of().formatHex(
            FileTransferHelper.newDigest("SHA-256").digest(CONTENT));

    final List<String> observedRanges = new CopyOnWriteArrayList<>();
    final List<String> observed416Ranges = new CopyOnWriteArrayList<>();
    final java.util.concurrent.atomic.AtomicInteger fullRequestsOn416 = new java.util.concurrent.atomic.AtomicInteger();
    final AtomicReference<String> uploadFileName = new AtomicReference<>();
    final AtomicReference<String> uploadSha256 = new AtomicReference<>();
    final AtomicReference<String> uploadContentType = new AtomicReference<>();
    final AtomicReference<String> uploadMethod = new AtomicReference<>();
    final AtomicReference<byte[]> uploadBody = new AtomicReference<>();

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        client = new JdkHttpClient(new HttpClientConfig());
        client.start();

        server.createContext("/upload", exchange -> {
            uploadFileName.set(exchange.getRequestHeaders().getFirst("x-file-name"));
            uploadSha256.set(exchange.getRequestHeaders().getFirst("x-file-sha256"));
            uploadContentType.set(exchange.getRequestHeaders().getFirst("Content-Type"));
            uploadMethod.set(exchange.getRequestMethod());
            uploadBody.set(exchange.getRequestBody().readAllBytes());
            respond(exchange, 200, "ok".getBytes(StandardCharsets.UTF_8));
        });

        server.createContext("/file", exchange -> {
            observedRanges.add(exchange.getRequestHeaders().getFirst("Range"));
            String range = exchange.getRequestHeaders().getFirst("Range");
            byte[] body;
            int status;
            if (range != null && range.startsWith("bytes=")) {
                long from = Long.parseLong(range.substring("bytes=".length(), range.length() - 1));
                body = new byte[(int) (CONTENT.length - from)];
                System.arraycopy(CONTENT, (int) from, body, 0, body.length);
                status = 206;
                exchange.getResponseHeaders().set("Content-Range",
                        "bytes " + from + "-" + (CONTENT.length - 1) + "/" + CONTENT.length);
            } else {
                body = CONTENT;
                status = 200;
            }
            respond(exchange, status, body);
        });
        // 4.3-C6-003（plan 2561 裁定 J）：带 Range 恒回 416、无 Range 回整体内容（模板 = Apache /file-416）
        server.createContext("/file-416", exchange -> {
            if (!"/file-416".equals(exchange.getRequestURI().getPath())) {
                // HttpServer 上下文为前缀匹配：sidecar 探测（/file-416.sha256 等）不计入、回 404
                respond(exchange, 404, new byte[0]);
                return;
            }
            String range = exchange.getRequestHeaders().getFirst("Range");
            if (range != null) {
                observed416Ranges.add(range);
                respond(exchange, 416, "range not satisfiable".getBytes(StandardCharsets.UTF_8));
            } else {
                fullRequestsOn416.incrementAndGet();
                respond(exchange, 200, CONTENT);
            }
        });
        server.createContext("/file.sha256", exchange ->
                respond(exchange, 200, (CONTENT_SHA256 + "  file").getBytes(StandardCharsets.UTF_8)));
        server.start();
    }

    static void respond(HttpExchange exchange, int status, byte[] body) throws IOException {
        exchange.sendResponseHeaders(status, body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }

    @AfterEach
    void tearDown() {
        client.stop();
        server.stop(0);
    }

    private String url(String path) {
        return "http://127.0.0.1:" + server.getAddress().getPort() + path;
    }

    private File tempFile(byte[] content) throws IOException {
        File file = File.createTempFile("jdk-upload", ".bin");
        file.deleteOnExit();
        Files.write(file.toPath(), content);
        return file;
    }

    @Test
    public void testBinaryUpload() throws Exception {
        File file = tempFile(CONTENT);
        IHttpResponse response = client.uploadAsync(HttpRequest.post(url("/upload")),
                new DefaultHttpInputFile(file), null, null).toCompletableFuture().get(10, TimeUnit.SECONDS);

        assertEquals(200, response.getHttpStatus());
        assertEquals("PUT", uploadMethod.get());
        assertTrue(uploadContentType.get().startsWith("application/octet-stream"));
        assertArrayEquals(CONTENT, uploadBody.get());
        assertEquals(CONTENT_SHA256, uploadSha256.get());
    }

    @Test
    public void testBase64FormUpload() throws Exception {
        File file = tempFile(CONTENT);
        UploadOptions options = new UploadOptions();
        options.setMode(UploadMode.BASE64_FORM);
        options.setComputeSha256(true);
        IHttpResponse response = client.uploadAsync(HttpRequest.post(url("/upload")),
                new DefaultHttpInputFile(file), options, null).toCompletableFuture().get(10, TimeUnit.SECONDS);
        assertEquals(200, response.getHttpStatus());

        String contentType = uploadContentType.get();
        assertTrue(contentType.startsWith("multipart/form-data"), "content-type=" + contentType);
        String boundary = contentType.substring(contentType.indexOf("boundary=") + "boundary=".length()).trim();
        String body = new String(uploadBody.get(), StandardCharsets.UTF_8);
        String filePart = extractPart(body, boundary, "name=\"file\"");
        byte[] decoded = java.util.Base64.getDecoder().decode(filePart.trim());
        assertArrayEquals(CONTENT, decoded);
    }

    static String extractPart(String multipartBody, String boundary, String nameSelector) {
        for (String part : multipartBody.split("--" + boundary)) {
            int headerEnd = part.indexOf("\r\n\r\n");
            if (headerEnd < 0)
                continue;
            if (part.substring(0, headerEnd).contains(nameSelector)) {
                int end = part.lastIndexOf("\r\n");
                return part.substring(headerEnd + 4, end > headerEnd ? end : part.length());
            }
        }
        throw new IllegalStateException("part not found: " + nameSelector);
    }

    @Test
    public void testDownloadResumeFromPart() throws Exception {
        File target = File.createTempFile("jdk-dl", ".bin");
        target.deleteOnExit();
        assertTrue(target.delete());

        File part = FileTransferHelper.partFileOf(target);
        byte[] first = new byte[2048];
        System.arraycopy(CONTENT, 0, first, 0, first.length);
        Files.write(part.toPath(), first);

        IHttpResponse response = client.downloadAsync(HttpRequest.get(url("/file")),
                DefaultHttpOutputFile.create(target), null, null)
                .toCompletableFuture().get(20, TimeUnit.SECONDS);

        assertEquals(206, response.getHttpStatus());
        assertEquals(List.of("bytes=2048-"), observedRanges);
        assertArrayEquals(CONTENT, Files.readAllBytes(target.toPath()));
        assertFalse(part.exists());
    }

    @Test
    public void testDownloadSidecarChecksumAndProgress() throws Exception {
        File target = File.createTempFile("jdk-dl2", ".bin");
        target.deleteOnExit();
        assertTrue(target.delete());

        java.util.concurrent.atomic.AtomicLong maxProgress = new java.util.concurrent.atomic.AtomicLong();
        DownloadOptions options = new DownloadOptions();
        options.setProgressListener(new io.nop.api.core.util.progress.IProgressListener() {
            @Override
            public boolean isCancelled() {
                return false;
            }

            @Override
            public void onProgress(Object message, long progress, long total) {
                maxProgress.accumulateAndGet(progress, Math::max);
            }
        });

        IHttpResponse response = client.downloadAsync(HttpRequest.get(url("/file")),
                DefaultHttpOutputFile.create(target), options, null)
                .toCompletableFuture().get(20, TimeUnit.SECONDS);

        assertEquals(200, response.getHttpStatus());
        assertArrayEquals(CONTENT, Files.readAllBytes(target.toPath()));
        assertEquals(CONTENT.length, maxProgress.get());
    }

    @Test
    public void testDownloadChecksumMismatch() throws Exception {
        File target = File.createTempFile("jdk-dl3", ".bin");
        target.deleteOnExit();
        assertTrue(target.delete());

        DownloadOptions options = new DownloadOptions();
        options.setExpectedSha256(UUID.randomUUID().toString().replace("-", "").repeat(2));
        options.setFetchSidecarChecksum(false);

        Exception e = assertThrows(Exception.class, () -> client.downloadAsync(
                        HttpRequest.get(url("/file")), DefaultHttpOutputFile.create(target), options, null)
                .toCompletableFuture().get(20, TimeUnit.SECONDS));
        Throwable cause = e;
        boolean found = false;
        while (cause != null) {
            if (cause instanceof NopException && "nop.err.http.download-checksum-mismatch"
                    .equals(((NopException) cause).getErrorCode())) {
                found = true;
                break;
            }
            cause = cause.getCause();
        }
        assertTrue(found, "expected checksum mismatch, got: " + e);
        assertFalse(target.exists());
        assertFalse(FileTransferHelper.partFileOf(target).exists());
    }

    /**
     * 4.3-C6-003（plan 2561 裁定 J）钉：416 恢复——本地 .part 比远端「新」时服务端回 416，
     * 客户端删除 .part 后整体重传，重试请求必须已移除 range 头（模板 = Apache
     * testDownload416RecoversByFullDownload）。
     * 变异红形态：去掉 offset=0 时的 removeHeader("range") → 重试经 copyRequest 继承旧 range 头
     * 再次命中 416 → 以 416 状态收场、目标文件不产生 → 红。
     */
    @Test
    public void testDownload416RecoversByFullDownload() throws Exception {
        File target = File.createTempFile("jdk-dl-416", ".bin");
        target.deleteOnExit();
        assertTrue(target.delete());

        Files.write(FileTransferHelper.partFileOf(target).toPath(), CONTENT); // 比远端还"新"

        IHttpResponse response = client.downloadAsync(HttpRequest.get(url("/file-416")),
                DefaultHttpOutputFile.create(target), null, null)
                .toCompletableFuture().get(20, TimeUnit.SECONDS);
        assertEquals(200, response.getHttpStatus(), "416 后必须以无 Range 的整体重传恢复");
        assertArrayEquals(CONTENT, Files.readAllBytes(target.toPath()));
        // 首发带续传 Range（命中 416），恢复重试不得再带 Range
        assertEquals(List.of("bytes=" + CONTENT.length + "-"), observed416Ranges);
        assertEquals(1, fullRequestsOn416.get(), "恢复重试恰好一次且不带 Range 头");
    }
}
