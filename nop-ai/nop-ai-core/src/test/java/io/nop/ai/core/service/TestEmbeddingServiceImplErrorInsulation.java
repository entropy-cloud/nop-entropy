package io.nop.ai.core.service;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.nop.ai.core.api.document.AiDocument;
import io.nop.ai.core.api.embedding.EmbeddingOptions;
import io.nop.ai.core.api.support.VectorData;
import io.nop.api.core.config.AppConfig;
import io.nop.api.core.util.ICancelToken;
import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.http.api.client.DownloadOptions;
import io.nop.http.api.client.HttpRequest;
import io.nop.http.api.client.IHttpClient;
import io.nop.http.api.client.IHttpInputFile;
import io.nop.http.api.client.IHttpOutputFile;
import io.nop.http.api.client.IHttpResponse;
import io.nop.http.api.client.UploadOptions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * MA-07/C4-003[3.1] regression (plan 2496 Phase 4, MA-07 sixth site beyond 30-pt
 * table #23–27): the embedding dispatch/retry chain must keep a deterministic
 * Error escape terminal and observable.
 *
 * <ul>
 *   <li>dispatch callback width: an Error thrown inside the HTTP completion
 *       callback (parse path) must terminalize the caller future instead of
 *       vanishing into the client's callback chain (pre-fix: embedAllAsync hangs
 *       forever, zero trace);</li>
 *   <li>dispatch sync width: an Error thrown synchronously by the HTTP client
 *       must terminalize the caller future (pre-fix: propagates raw to the
 *       embedAllAsync caller);</li>
 *   <li>scheduleRetry face: the retry-leg ScheduledFuture is no longer silently
 *       discarded — a retry leg dying on a Throwable is observed via the
 *       {@code nop.ai.embedding.retry-task-fail} ERROR log.</li>
 * </ul>
 */
public class TestEmbeddingServiceImplErrorInsulation extends JunitBaseTestCase {

    private static final String PROVIDER = "test-embed";

    private ErrorHttpClient httpClient;
    private EmbeddingServiceImpl service;

    @BeforeEach
    void setUp() {
        AppConfig.getConfigProvider().assignConfigValue("nop.ai.llm." + PROVIDER + ".api-key",
                "sk-embed-primary");
        AppConfig.getConfigProvider().assignConfigValue("nop.ai.llm." + PROVIDER + ".base-url",
                "https://primary-embed.example.com/v1");
        httpClient = new ErrorHttpClient();
        service = new EmbeddingServiceImpl();
        service.setHttpClient(httpClient);
    }

    @AfterEach
    void tearDown() {
        LlmConfigHelper.reset();
    }

    private EmbeddingOptions options() {
        EmbeddingOptions opts = new EmbeddingOptions();
        opts.setProvider(PROVIDER);
        return opts;
    }

    @Test
    void testErrorInsideDispatchCallbackTerminalizesCallerFuture() throws Exception {
        // parseSuccess reads the body: the Error fires inside the whenComplete callback.
        httpClient.nextResponse = new ErrorBodyResponse();

        CompletableFuture<List<VectorData>> future =
                service.embedAllAsync(List.of(AiDocument.fromText("boom")), options())
                        .toCompletableFuture();

        // Pre-fix: the LinkageError escaped the callback's Exception catch and the
        // caller future never completed (hang). Post-fix: terminal with the Error.
        try {
            List<VectorData> ignored = future.get(5, TimeUnit.SECONDS);
            throw new AssertionError("expected an exceptional terminal state");
        } catch (java.util.concurrent.ExecutionException expected) {
            assertTrue(expected.getCause() instanceof LinkageError,
                    "the injected Error must be carried by the caller future: " + expected.getCause());
        }
        assertTrue(future.isCompletedExceptionally(), "caller future must be terminal");
    }

    @Test
    void testSyncErrorFromHttpClientTerminalizesCallerFuture() throws Exception {
        httpClient.throwSyncError = true;

        // Pre-fix: the LinkageError propagated raw out of embedAllAsync to the caller.
        CompletableFuture<List<VectorData>> future =
                service.embedAllAsync(List.of(AiDocument.fromText("boom")), options())
                        .toCompletableFuture();

        assertTrue(future.isCompletedExceptionally(),
                "the sync Error must terminalize the caller future exceptionally");
        try {
            future.get(5, TimeUnit.SECONDS);
            throw new AssertionError("expected an exceptional terminal state");
        } catch (java.util.concurrent.ExecutionException expected) {
            assertTrue(expected.getCause() instanceof LinkageError,
                    "the injected Error must be carried by the caller future: " + expected.getCause());
        }
    }

    @Test
    void testRetryLegErrorIsObservedViaGuardLog() throws Exception {
        Logger logger = (Logger) LoggerFactory.getLogger(EmbeddingServiceImpl.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        ScheduledExecutorService realScheduler =
                Executors.newSingleThreadScheduledExecutor(r -> {
                    Thread t = new Thread(r, "test-embed-retry-guard");
                    t.setDaemon(true);
                    return t;
                });
        try {
            service.setRetryScheduler(realScheduler);
            // The real (non-overridden) scheduleRetry guard must catch the Throwable and
            // log it; pre-fix the raw schedule() dropped both the future and the failure.
            service.scheduleRetry(0, () -> {
                throw new LinkageError("injected retry-leg Error");
            });

            long deadline = System.currentTimeMillis() + 5_000;
            while (appender.list.stream().noneMatch(
                    e -> e.getFormattedMessage().contains("nop.ai.embedding.retry-task-fail"))
                    && System.currentTimeMillis() < deadline) {
                Thread.sleep(10);
            }
            assertTrue(appender.list.stream().anyMatch(
                            e -> e.getFormattedMessage().contains("nop.ai.embedding.retry-task-fail")),
                    "retry-leg death must be observable via the guard ERROR log");
        } finally {
            logger.detachAppender(appender);
            appender.stop();
            realScheduler.shutdownNow();
        }
    }

    /** Stub that can inject a deterministic Error on the dispatch path. */
    static class ErrorHttpClient implements IHttpClient {
        IHttpResponse nextResponse;
        boolean throwSyncError;

        @Override
        public CompletionStage<IHttpResponse> fetchAsync(HttpRequest request, ICancelToken cancelToken) {
            if (throwSyncError) {
                throw new LinkageError("injected dispatch Error");
            }
            return CompletableFuture.completedFuture(nextResponse);
        }

        @Override
        public CompletionStage<IHttpResponse> downloadAsync(HttpRequest request, IHttpOutputFile targetFile,
                                                            DownloadOptions options, ICancelToken cancelToken) {
            throw new UnsupportedOperationException();
        }

        @Override
        public CompletionStage<IHttpResponse> uploadAsync(HttpRequest request, IHttpInputFile inputFile,
                                                          UploadOptions options, ICancelToken cancelToken) {
            throw new UnsupportedOperationException();
        }
    }

    /** Response whose body read throws a deterministic Error (parse-path injection). */
    static class ErrorBodyResponse implements IHttpResponse {
        @Override
        public int getHttpStatus() {
            return 200;
        }

        @Override
        public String getContentType() {
            return "application/json";
        }

        @Override
        public String getCharset() {
            return "UTF-8";
        }

        @Override
        public byte[] getBodyAsBytes() {
            throw new LinkageError("injected parse Error");
        }

        @Override
        public String getBodyAsString() {
            throw new LinkageError("injected parse Error");
        }

        @Override
        public <T> T getBodyAsBean(Class<T> beanClass) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Object getBody() {
            throw new LinkageError("injected parse Error");
        }

        @Override
        public Map<String, String> getHeaders() {
            return Map.of();
        }
    }
}
