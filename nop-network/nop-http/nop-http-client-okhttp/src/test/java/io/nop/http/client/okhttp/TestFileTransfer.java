package io.nop.http.client.okhttp;

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
import okhttp3.OkHttpClient;
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
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * OkHttp 客户端文件传输回归：与 Apache/JDK 实现行为等价
 */
@Timeout(60)
@EnabledIf("io.nop.http.client.okhttp.HttpFileTransferTestCondition#isFileTransferReliableOnThisOS")
public class TestFileTransfer {

    private HttpServer server;
    private OkHttpClientImpl client;

    static final byte[] CONTENT = ("okhttp file transfer 内容\n" + "z".repeat(50_000)).getBytes(StandardCharsets.UTF_8);
    static final String CONTENT_SHA256 = java.util.HexFormat.of().formatHex(
            FileTransferHelper.newDigest("SHA-256").digest(CONTENT));

    final List<String> observedRanges = new CopyOnWriteArrayList<>();
    final AtomicReference<String> uploadSha256 = new AtomicReference<>();
    final AtomicReference<String> uploadContentType = new AtomicReference<>();
    final AtomicReference<String> uploadMethod = new AtomicReference<>();
    final AtomicReference<byte[]> uploadBody = new AtomicReference<>();

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        client = new OkHttpClientImpl(new OkHttpClient.Builder().build(), new HttpClientConfig());

        server.createContext("/upload", exchange -> {
            uploadSha256.set(exchange.getRequestHeaders().getFirst("x-file-sha256"));
            uploadContentType.set(exchange.getRequestHeaders().getFirst("Content-Type"));
            uploadMethod.set(exchange.getRequestMethod());
            uploadBody.set(exchange.getRequestBody().readAllBytes());
            exchange.sendResponseHeaders(200, 2);
            exchange.getResponseBody().write("ok".getBytes(StandardCharsets.UTF_8));
            exchange.close();
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
            exchange.sendResponseHeaders(status, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.createContext("/file.sha256", exchange -> {
            byte[] sidecar = (CONTENT_SHA256 + "  file").getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, sidecar.length);
            exchange.getResponseBody().write(sidecar);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    private String url(String path) {
        return "http://127.0.0.1:" + server.getAddress().getPort() + path;
    }

    @Test
    public void testBinaryUpload() throws Exception {
        File file = File.createTempFile("ok-upload", ".bin");
        file.deleteOnExit();
        Files.write(file.toPath(), CONTENT);

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
        File file = File.createTempFile("ok-upload2", ".bin");
        file.deleteOnExit();
        Files.write(file.toPath(), CONTENT);

        UploadOptions options = new UploadOptions();
        options.setMode(UploadMode.BASE64_FORM);
        IHttpResponse response = client.uploadAsync(HttpRequest.post(url("/upload")),
                new DefaultHttpInputFile(file), options, null).toCompletableFuture().get(10, TimeUnit.SECONDS);
        assertEquals(200, response.getHttpStatus());

        String contentType = uploadContentType.get();
        assertTrue(contentType.startsWith("multipart/form-data"), "content-type=" + contentType);
        String body = new String(uploadBody.get(), StandardCharsets.UTF_8);
        // base64 字段内容可完整解码回原文
        int nameIdx = body.indexOf("name=\"file\"");
        int headerEnd = body.indexOf("\r\n\r\n", nameIdx);
        String b64 = body.substring(headerEnd + 4, body.indexOf("--", headerEnd)).trim();
        assertArrayEquals(CONTENT, java.util.Base64.getDecoder().decode(b64));
    }

    @Test
    public void testDownloadResumeFromPart() throws Exception {
        File target = File.createTempFile("ok-dl", ".bin");
        target.deleteOnExit();
        assertTrue(target.delete());

        File part = FileTransferHelper.partFileOf(target);
        byte[] first = new byte[1000];
        System.arraycopy(CONTENT, 0, first, 0, first.length);
        Files.write(part.toPath(), first);

        IHttpResponse response = client.downloadAsync(HttpRequest.get(url("/file")),
                DefaultHttpOutputFile.create(target), null, null)
                .toCompletableFuture().get(20, TimeUnit.SECONDS);

        assertEquals(206, response.getHttpStatus());
        assertEquals(List.of("bytes=1000-"), observedRanges);
        assertArrayEquals(CONTENT, Files.readAllBytes(target.toPath()));
        assertFalse(part.exists());
    }

    @Test
    public void testDownloadChecksumMismatchFails() throws Exception {
        File target = File.createTempFile("ok-dl2", ".bin");
        target.deleteOnExit();
        assertTrue(target.delete());

        DownloadOptions options = new DownloadOptions();
        // sidecar 返回正确摘要，用错误显式期望触发失败
        options.setExpectedSha256("deadbeef".repeat(8));
        options.setFetchSidecarChecksum(false);

        Exception e = org.junit.jupiter.api.Assertions.assertThrows(Exception.class,
                () -> client.downloadAsync(HttpRequest.get(url("/file")), DefaultHttpOutputFile.create(target),
                        options, null).toCompletableFuture().get(20, TimeUnit.SECONDS));
        boolean found = false;
        Throwable cause = e;
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
}
