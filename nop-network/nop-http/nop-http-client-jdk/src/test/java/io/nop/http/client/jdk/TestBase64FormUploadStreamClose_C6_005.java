package io.nop.http.client.jdk;

import com.sun.net.httpserver.HttpServer;
import io.nop.http.api.client.HttpClientConfig;
import io.nop.http.api.client.HttpRequest;
import io.nop.http.api.client.IHttpResponse;
import io.nop.http.api.support.DefaultHttpInputFile;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.File;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * C6-005 (plan 2522): BASE64_FORM upload reads the whole file via
 * IoHelper.readBytes(inputFile.getInputStream()) WITHOUT closing the stream — every
 * upload leaks one file descriptor (three client implementations share the miss).
 *
 * <p>Evidence form: a tracking IHttpInputFile records whether the stream handed to the
 * upload path was closed. Deterministic (not fd-count based — the fd variant proved
 * GC-flaky on the Apache path in the defect-baseline red run, false-green).</p>
 */
@Timeout(120)
public class TestBase64FormUploadStreamClose_C6_005 {

    /** Wraps the default file input and counts close() on the handed-out stream. */
    static final class TrackingInputFile extends DefaultHttpInputFile {
        final AtomicInteger closeCount = new AtomicInteger();

        TrackingInputFile(File file) {
            super(file);
        }

        @Override
        public InputStream getInputStream() {
            InputStream delegate = super.getInputStream();
            return new FilterInputStream(delegate) {
                @Override
                public void close() throws IOException {
                    closeCount.incrementAndGet();
                    super.close();
                }
            };
        }
    }

    static final int UPLOADS = 3;

    private HttpServer server;
    private JdkHttpClient client;

    @BeforeEach
    void setUp() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        client = new JdkHttpClient(new HttpClientConfig());
        client.start();
        byte[] body = "<ok/>".getBytes(StandardCharsets.UTF_8);
        server.createContext("/upload", exchange -> {
            exchange.getRequestBody().readAllBytes();
            exchange.getResponseHeaders().set("Content-Type", "text/xml");
            exchange.sendResponseHeaders(200, body.length);
            OutputStream os = exchange.getResponseBody();
            os.write(body);
            os.close();
        });
        server.start();
    }

    @AfterEach
    void tearDown() {
        client.stop();
        server.stop(0);
    }

    @Test
    public void base64FormUploadClosesTheInputStream() throws Exception {
        File input = Files.createTempFile("c6p0-tracking", ".bin").toFile();
        Files.write(input.toPath(), new byte[32 * 1024]);
        TrackingInputFile tracked = new TrackingInputFile(input);
        try {
            HttpRequest request = HttpRequest
                    .post("http://127.0.0.1:" + server.getAddress().getPort() + "/upload");
            io.nop.http.api.client.UploadOptions options = new io.nop.http.api.client.UploadOptions();
            options.setMode(io.nop.http.api.client.UploadMode.BASE64_FORM);
            CompletionStage<IHttpResponse> stage = client.uploadAsync(request, tracked, options, null);
            IHttpResponse response = stage.toCompletableFuture().get(30, TimeUnit.SECONDS);
            assertEquals(200, response.getHttpStatus());

            assertTrue(tracked.closeCount.get() >= 1,
                    "C6-005: BASE64_FORM upload must close the input stream handed to"
                            + " IoHelper.readBytes (defect: never closed — one leaked fd per upload)");
        } finally {
            Files.deleteIfExists(input.toPath());
        }
    }
}
