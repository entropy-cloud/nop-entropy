package io.nop.http.apache;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.nop.api.core.exceptions.NopException;
import io.nop.http.api.client.HttpClientConfig;
import io.nop.http.api.client.HttpRequest;
import io.nop.http.api.client.IHttpResponse;
import io.nop.http.api.client.UploadMode;
import io.nop.http.api.client.UploadOptions;
import io.nop.http.api.client.DownloadOptions;
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
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 文件传输协议回归：上传双模式 / 断点续传 / 摘要校验 / 416 恢复。
 * 协议契约见 ai-dev/design/nop-network/file-transfer-design.md
 */
@Timeout(60)
@EnabledIf("io.nop.http.apache.HttpFileTransferTestCondition#isFileTransferReliableOnThisOS")
public class TestFileTransfer {

    private HttpServer server;
    private ApacheHttpClient client;

    static final byte[] CONTENT = ("hello file transfer 中文内容 line-2\n" + "x".repeat(100_000)).getBytes(StandardCharsets.UTF_8);
    static final String CONTENT_SHA256 = HexFormat.of().formatHex(
            FileTransferHelper.newDigest("SHA-256").digest(CONTENT));

    // 服务端观测
    final List<String> observedRanges = new CopyOnWriteArrayList<>();
    final AtomicReference<String> uploadContentType = new AtomicReference<>();
    final AtomicReference<String> uploadFileName = new AtomicReference<>();
    final AtomicReference<String> uploadSha256 = new AtomicReference<>();
    final AtomicReference<String> uploadMode = new AtomicReference<>();
    final AtomicReference<byte[]> uploadBody = new AtomicReference<>();
    final AtomicReference<String> uploadMethod = new AtomicReference<>();

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        client = new ApacheHttpClient(new HttpClientConfig());
        client.start();

        server.createContext("/upload", exchange -> {
            uploadContentType.set(exchange.getRequestHeaders().getFirst("Content-Type"));
            uploadFileName.set(exchange.getRequestHeaders().getFirst("x-file-name"));
            uploadSha256.set(exchange.getRequestHeaders().getFirst("x-file-sha256"));
            uploadMode.set(exchange.getRequestHeaders().getFirst("x-file-mode"));
            uploadMethod.set(exchange.getRequestMethod());
            uploadBody.set(exchange.getRequestBody().readAllBytes());
            respond(exchange, 200, "ok".getBytes(StandardCharsets.UTF_8));
        });

        server.createContext("/file", exchange -> serveRange(exchange, CONTENT, CONTENT_SHA256, null, observedRanges));
        server.createContext("/file-bad-sha", exchange -> serveRange(exchange, CONTENT, "00" + CONTENT_SHA256.substring(2), null, null));
        server.createContext("/file-no-range", exchange -> {
            observedRanges.add(exchange.getRequestHeaders().getFirst("Range"));
            respond(exchange, 200, CONTENT);
        });
        server.createContext("/file-416", exchange -> {
            if (exchange.getRequestHeaders().getFirst("Range") != null) {
                observedRanges.add(exchange.getRequestHeaders().getFirst("Range"));
                respond(exchange, 416, "range not satisfiable".getBytes(StandardCharsets.UTF_8));
            } else {
                respond(exchange, 200, CONTENT);
            }
        });
        server.createContext("/file.sha256", exchange ->
                respond(exchange, 200, (CONTENT_SHA256 + "  file").getBytes(StandardCharsets.UTF_8)));
        server.start();
    }

    private void serveRange(HttpExchange exchange, byte[] content, String sha256Header,
                            String headerName, List<String> ranges) throws IOException {
        if (ranges != null)
            ranges.add(exchange.getRequestHeaders().getFirst("Range"));
        String range = exchange.getRequestHeaders().getFirst("Range");
        byte[] body;
        int status;
        if (range != null && range.startsWith("bytes=")) {
            long from = Long.parseLong(range.substring("bytes=".length(), range.length() - 1));
            body = new byte[(int) (content.length - from)];
            System.arraycopy(content, (int) from, body, 0, body.length);
            status = 206;
            exchange.getResponseHeaders().set("Content-Range",
                    "bytes " + from + "-" + (content.length - 1) + "/" + content.length);
        } else {
            body = content;
            status = 200;
        }
        if (sha256Header != null)
            exchange.getResponseHeaders().set("x-content-sha256", sha256Header);
        respond(exchange, status, body);
    }

    static void respond(HttpExchange exchange, int status, byte[] body) throws IOException {
        exchange.sendResponseHeaders(status, body.length == 0 && status == 200 ? -1 : body.length);
        if (body.length > 0)
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

    private File tempFile(String prefix, byte[] content) throws IOException {
        File file = File.createTempFile(prefix, ".bin");
        file.deleteOnExit();
        Files.write(file.toPath(), content);
        return file;
    }

    @Test
    public void testBinaryUpload() throws Exception {
        File file = tempFile("upload-bin", CONTENT);
        IHttpResponse response = client.uploadAsync(HttpRequest.post(url("/upload")),
                new DefaultHttpInputFile(file), null, null).toCompletableFuture().get(10, TimeUnit.SECONDS);

        assertEquals(200, response.getHttpStatus());
        assertEquals("PUT", uploadMethod.get());
        assertTrue(uploadContentType.get().startsWith("application/octet-stream"));
        assertEquals(String.valueOf(CONTENT.length),
                String.valueOf(uploadBody.get().length));
        assertArrayEquals(CONTENT, uploadBody.get());
        assertEquals(CONTENT_SHA256, uploadSha256.get());
        assertEquals("binary", uploadMode.get());
        assertTrue(uploadFileName.get().contains("upload-bin"));
    }

    @Test
    public void testBinaryUploadCustomMethod() throws Exception {
        File file = tempFile("upload-m", CONTENT);
        UploadOptions options = new UploadOptions();
        options.setHttpMethod("POST");
        options.setComputeSha256(false);
        client.uploadAsync(HttpRequest.post(url("/upload")), new DefaultHttpInputFile(file), options, null)
                .toCompletableFuture().get(10, TimeUnit.SECONDS);
        assertEquals("POST", uploadMethod.get());
        assertEquals(null, uploadSha256.get());
    }

    @Test
    public void testBase64FormUpload() throws Exception {
        File file = tempFile("报表 file.txt", CONTENT);
        UploadOptions options = new UploadOptions();
        options.setMode(UploadMode.BASE64_FORM);
        IHttpResponse response = client.uploadAsync(HttpRequest.post(url("/upload")),
                new DefaultHttpInputFile(file), options, null).toCompletableFuture().get(10, TimeUnit.SECONDS);
        assertEquals(200, response.getHttpStatus());

        String contentType = uploadContentType.get();
        assertTrue(contentType.startsWith("multipart/form-data"), "content-type=" + contentType);
        String boundary = contentType.substring(contentType.indexOf("boundary=") + "boundary=".length()).trim();

        String body = new String(uploadBody.get(), StandardCharsets.UTF_8);
        // 解析 multipart：filename 字段 + file 字段（base64 文本）
        String filePart = extractPart(body, boundary, "name=\"file\"");
        String namePart = extractPart(body, boundary, "name=\"filename\"");
        byte[] decoded = java.util.Base64.getDecoder().decode(filePart.trim());
        assertArrayEquals(CONTENT, decoded);
        // createTempFile 会追加随机后缀，校验前后缀即可
        assertTrue(namePart.trim().startsWith("报表 file.txt"), "namePart=" + namePart);
        assertTrue(namePart.trim().endsWith(".bin"), "namePart=" + namePart);
    }

    static String extractPart(String multipartBody, String boundary, String nameSelector) {
        for (String part : multipartBody.split("--" + boundary)) {
            int headerEnd = part.indexOf("\r\n\r\n");
            if (headerEnd < 0)
                continue;
            String headers = part.substring(0, headerEnd);
            if (headers.contains(nameSelector)) {
                return part.substring(headerEnd + 4, part.lastIndexOf("\r\n") > headerEnd ? part.lastIndexOf("\r\n") : part.length());
            }
        }
        throw new IllegalStateException("part not found: " + nameSelector);
    }

    @Test
    public void testDownloadFull() throws Exception {
        File target = File.createTempFile("download-full", ".bin");
        target.deleteOnExit();
        assertTrue(target.delete());

        IHttpResponse response = client.downloadAsync(HttpRequest.get(url("/file")),
                DefaultHttpOutputFile.create(target), null, null)
                .toCompletableFuture().get(20, TimeUnit.SECONDS);
        assertEquals(200, response.getHttpStatus());
        assertArrayEquals(CONTENT, Files.readAllBytes(target.toPath()));
        assertFalse(FileTransferHelper.partFileOf(target).exists(), "part file must be renamed");
    }

    @Test
    public void testDownloadResumeFromPart() throws Exception {
        File target = File.createTempFile("download-resume", ".bin");
        target.deleteOnExit();
        assertTrue(target.delete());

        // 预置 .part：已下载前 1KB
        File part = FileTransferHelper.partFileOf(target);
        byte[] first = new byte[1024];
        System.arraycopy(CONTENT, 0, first, 0, first.length);
        Files.write(part.toPath(), first);

        IHttpResponse response = client.downloadAsync(HttpRequest.get(url("/file")),
                DefaultHttpOutputFile.create(target), null, null)
                .toCompletableFuture().get(20, TimeUnit.SECONDS);

        assertEquals(206, response.getHttpStatus());
        // 服务端只收到剩余区间的 Range 请求
        assertEquals(List.of("bytes=1024-"), observedRanges);
        assertArrayEquals(CONTENT, Files.readAllBytes(target.toPath()));
        assertFalse(part.exists(), "part file must be renamed after completion");
    }

    @Test
    public void testDownloadServerWithoutRangeSupport() throws Exception {
        File target = File.createTempFile("download-norange", ".bin");
        target.deleteOnExit();
        assertTrue(target.delete());

        // 预置陈旧 .part，服务端恒回 200 整体内容
        Files.write(FileTransferHelper.partFileOf(target).toPath(), "stale".getBytes(StandardCharsets.UTF_8));

        IHttpResponse response = client.downloadAsync(HttpRequest.get(url("/file-no-range")),
                DefaultHttpOutputFile.create(target), null, null)
                .toCompletableFuture().get(20, TimeUnit.SECONDS);
        assertEquals(200, response.getHttpStatus());
        assertArrayEquals(CONTENT, Files.readAllBytes(target.toPath()));
    }

    @Test
    public void testDownload416RecoversByFullDownload() throws Exception {
        File target = File.createTempFile("download-416", ".bin");
        target.deleteOnExit();
        assertTrue(target.delete());

        Files.write(FileTransferHelper.partFileOf(target).toPath(), CONTENT); // 比远端还"新"

        IHttpResponse response = client.downloadAsync(HttpRequest.get(url("/file-416")),
                DefaultHttpOutputFile.create(target), null, null)
                .toCompletableFuture().get(20, TimeUnit.SECONDS);
        assertEquals(200, response.getHttpStatus());
        assertArrayEquals(CONTENT, Files.readAllBytes(target.toPath()));
    }

    @Test
    public void testDownloadChecksumHeaderMismatchFails() throws Exception {
        File target = File.createTempFile("download-badsha", ".bin");
        target.deleteOnExit();
        assertTrue(target.delete());

        DownloadOptions options = new DownloadOptions();
        options.setFetchSidecarChecksum(false);
        options.setRequireChecksum(true);

        Exception e = assertThrows(Exception.class, () -> client.downloadAsync(
                        HttpRequest.get(url("/file-bad-sha")), DefaultHttpOutputFile.create(target), options, null)
                .toCompletableFuture().get(20, TimeUnit.SECONDS));
        assertTrue(findError(e, "download-checksum-mismatch"), "expected checksum mismatch, got: " + e);
        assertFalse(target.exists(), "target must not be created on checksum failure");
        assertFalse(FileTransferHelper.partFileOf(target).exists(), "corrupt part must be deleted");
    }

    @Test
    public void testDownloadSidecarChecksum() throws Exception {
        File target = File.createTempFile("download-sidecar", ".bin");
        target.deleteOnExit();
        assertTrue(target.delete());

        // 无显式来源与响应头：探测 {url}.sha256
        IHttpResponse response = client.downloadAsync(HttpRequest.get(url("/file")),
                DefaultHttpOutputFile.create(target), null, null)
                .toCompletableFuture().get(20, TimeUnit.SECONDS);
        assertEquals(200, response.getHttpStatus());
        assertArrayEquals(CONTENT, Files.readAllBytes(target.toPath()));
    }

    @Test
    public void testDownloadRequireChecksumWithoutSource() throws Exception {
        File target = File.createTempFile("download-nosrc", ".bin");
        target.deleteOnExit();
        assertTrue(target.delete());

        DownloadOptions options = new DownloadOptions();
        options.setFetchSidecarChecksum(false);
        options.setRequireChecksum(true);

        Exception e = assertThrows(Exception.class, () -> client.downloadAsync(
                        HttpRequest.get(url("/file-no-range")).param("noSidecar", "1"),
                        DefaultHttpOutputFile.create(target), options, null)
                .toCompletableFuture().get(20, TimeUnit.SECONDS));
        assertTrue(findError(e, "download-no-checksum"), "expected no-checksum error, got: " + e);
    }

    @Test
    public void testUploadProgressReported() throws Exception {
        File file = tempFile("upload-prog", CONTENT);
        java.util.concurrent.atomic.AtomicLong maxProgress = new java.util.concurrent.atomic.AtomicLong();
        UploadOptions options = new UploadOptions();
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
        client.uploadAsync(HttpRequest.post(url("/upload")), new DefaultHttpInputFile(file), options, null)
                .toCompletableFuture().get(20, TimeUnit.SECONDS);
        assertEquals(CONTENT.length, maxProgress.get());
    }

    static boolean findError(Throwable e, String errorCode) {
        while (e != null) {
            if (e instanceof NopException && errorCode.equals(((NopException) e).getErrorCode()))
                return true;
            if (e.getMessage() != null && e.getMessage().contains(errorCode))
                return true;
            e = e.getCause();
        }
        return false;
    }
}
