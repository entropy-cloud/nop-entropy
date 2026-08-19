package io.nop.job.api.log;

import io.nop.http.api.client.HttpRequest;
import io.nop.http.api.client.IHttpClient;
import io.nop.http.api.client.IHttpResponse;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.ICancelToken;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import static io.nop.job.api.JobApiErrors.ERR_JOB_LOG_REPORT_DISABLED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * plan 2254 Phase 4: JobLogReporter（worker 侧日志上报 client）——未配置地址显式关闭、
 * 配置后 best-effort 异步上报、失败不影响调用方。
 */
public class TestJobLogReporter {

    @Test
    public void testDisabledWhenNoUrl() {
        JobLogReporter reporter = new JobLogReporter();
        reporter.setHttpClient(new MockHttpClient());

        assertFalse(reporter.isEnabled(), "reporter disabled without report-url");

        TaskLogEntry entry = entry();
        NopException e = assertThrows(NopException.class, () -> reporter.report(entry));
        assertEquals(ERR_JOB_LOG_REPORT_DISABLED.getErrorCode(), e.getErrorCode());
    }

    @Test
    public void testDisabledWhenNoHttpClient() {
        JobLogReporter reporter = new JobLogReporter();
        reporter.setReportUrl("http://coordinator/r/NopJobTaskLog-reportTaskLog");

        assertFalse(reporter.isEnabled(), "reporter disabled without IHttpClient");
        assertNull(reporter.reportAsync(entry()).toCompletableFuture().join(),
                "disabled reporter no-ops without error");
    }

    @Test
    public void testEnabledAndPosts() {
        MockHttpClient client = new MockHttpClient();
        JobLogReporter reporter = new JobLogReporter();
        reporter.setReportUrl("http://coordinator/r/NopJobTaskLog-reportTaskLog");
        reporter.setHttpClient(client);

        assertTrue(reporter.isEnabled());

        TaskLogEntry entry = entry();
        reporter.report(entry);

        assertNotNull(client.lastRequest, "request must be sent when enabled");
        assertEquals("http://coordinator/r/NopJobTaskLog-reportTaskLog", client.lastRequest.getUrl());
        assertTrue(client.lastRequest.getBody() != null, "body carries the log entry");
    }

    @Test
    public void testAsyncFailureIsBestEffort() {
        MockHttpClient client = new MockHttpClient();
        client.fail = true;
        JobLogReporter reporter = new JobLogReporter();
        reporter.setReportUrl("http://coordinator/r/NopJobTaskLog-reportTaskLog");
        reporter.setHttpClient(client);

        TaskLogEntry entry = entry();
        CompletionStage<Void> stage = reporter.reportAsync(entry);

        assertNull(stage.toCompletableFuture().join(), "async failure must not propagate to caller");
    }

    @Test
    public void testHttpErrorIsLoggedNotThrown() {
        MockHttpClient client = new MockHttpClient();
        client.status = 500;
        JobLogReporter reporter = new JobLogReporter();
        reporter.setReportUrl("http://coordinator/r/NopJobTaskLog-reportTaskLog");
        reporter.setHttpClient(client);

        TaskLogEntry entry = entry();
        CompletionStage<Void> stage = reporter.reportAsync(entry);

        assertNull(stage.toCompletableFuture().join(), "HTTP error must not propagate to caller");
    }

    private TaskLogEntry entry() {
        TaskLogEntry entry = new TaskLogEntry();
        entry.setJobTaskId("task-1");
        entry.setLogTime(System.currentTimeMillis());
        entry.setLogLevel("INFO");
        entry.setLogMessage("hello");
        entry.setLogPayload(Map.of("progress", 10));
        return entry;
    }

    static class MockHttpClient implements IHttpClient {
        volatile HttpRequest lastRequest;
        volatile boolean fail;
        volatile int status = 200;

        @Override
        public CompletionStage<IHttpResponse> fetchAsync(HttpRequest request, ICancelToken cancelToken) {
            lastRequest = request;
            if (fail) {
                return CompletableFuture.failedFuture(new RuntimeException("connection refused"));
            }
            return CompletableFuture.completedFuture(new MockResponse(status));
        }

        @Override
        public CompletionStage<IHttpResponse> downloadAsync(HttpRequest request,
                                                            io.nop.http.api.client.IHttpOutputFile targetFile,
                                                            io.nop.http.api.client.DownloadOptions options,
                                                            ICancelToken cancelToken) {
            return CompletableFuture.completedFuture(new MockResponse(status));
        }

        @Override
        public CompletionStage<IHttpResponse> uploadAsync(HttpRequest request,
                                                          io.nop.http.api.client.IHttpInputFile inputFile,
                                                          io.nop.http.api.client.UploadOptions options,
                                                          ICancelToken cancelToken) {
            return CompletableFuture.completedFuture(new MockResponse(status));
        }
    }

    static class MockResponse implements IHttpResponse {
        private final int status;

        MockResponse(int status) {
            this.status = status;
        }

        @Override
        public int getHttpStatus() {
            return status;
        }

        @Override
        public Map<String, String> getHeaders() {
            return java.util.Collections.emptyMap();
        }

        @Override
        public String getContentType() {
            return "application/json";
        }

        @Override
        public String getCharset() {
            return "utf-8";
        }

        @Override
        public byte[] getBodyAsBytes() {
            return new byte[0];
        }

        @Override
        public String getBodyAsString() {
            return "{}";
        }

        @Override
        public <T> T getBodyAsBean(Class<T> beanClass) {
            return null;
        }

        @Override
        public Object getBody() {
            return null;
        }
    }
}
