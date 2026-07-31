package io.nop.ai.core.service;

import io.nop.ai.core.api.chat.AiChatOptions;
import io.nop.ai.core.api.messages.AiChatExchange;
import io.nop.ai.core.api.messages.Prompt;
import io.nop.api.core.util.FutureHelper;
import io.nop.api.core.util.ICancelToken;
import io.nop.commons.util.FileHelper;
import io.nop.core.resource.IResource;
import io.nop.core.resource.impl.FileResource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * MockAiChatService polls a response file (config {@code nop.ai.service.mock-dir}) for the
 * {@code NOP_EOF} marker which an external mock driver writes after the service creates the file.
 * These tests simulate that driver: keep (re)writing the response content until the service's poll
 * loop consumes it (the service truncates the file once before polling starts).
 */
public class TestMockAiChatService {

    private static final Logger LOG = LoggerFactory.getLogger(TestMockAiChatService.class);

    private static final String MARKER_EOF = "\nNOP_EOF";

    private final List<File> tempFiles = new ArrayList<>();

    private File newTempFile(String prefix, String suffix) throws IOException {
        File file = File.createTempFile(prefix, suffix);
        tempFiles.add(file);
        return file;
    }

    @AfterEach
    void cleanupTempFiles() {
        for (File file : tempFiles) {
            if (file.exists() && !FileHelper.deleteAll(file)) {
                LOG.warn("nop.test.fail-delete-temp-file:file={}", file);
            }
        }
        tempFiles.clear();
    }

    private static class FileMockAiChatService extends MockAiChatService {
        private final Map<String, File> filesByPostfix = new HashMap<>();
        private final File responseFile;

        FileMockAiChatService(TestMockAiChatService owner) throws Exception {
            responseFile = owner.newTempFile("mock-response", ".md");
            filesByPostfix.put("-request.md", owner.newTempFile("mock-request", ".md"));
            filesByPostfix.put("-prompt.md", owner.newTempFile("mock-prompt", ".md"));
            filesByPostfix.put("-response.md", responseFile);
        }

        @Override
        protected IResource getResource(AiChatExchange exchange, String postfix) {
            return new FileResource(filesByPostfix.get(postfix));
        }
    }

    private static AiChatOptions mockOptions() {
        AiChatOptions options = new AiChatOptions();
        options.setProvider("mock");
        options.setModel("mock");
        return options;
    }

    private static AiChatExchange sendAndWriteResponse(FileMockAiChatService service, String content) throws Exception {
        CompletionStage<AiChatExchange> stage = service.sendChatAsync(Prompt.userText("hello"), mockOptions(), null);
        long deadline = System.currentTimeMillis() + 5000;
        while (System.currentTimeMillis() < deadline) {
            Files.write(service.responseFile.toPath(), (content + MARKER_EOF).getBytes(StandardCharsets.UTF_8));
            if (stage.toCompletableFuture().isDone())
                break;
            Thread.sleep(50);
        }
        return FutureHelper.syncGet(stage);
    }

    @Test
    public void testRoundTrip() throws Exception {
        FileMockAiChatService service = new FileMockAiChatService(this);

        Prompt prompt = Prompt.userText("hello");
        AiChatExchange exchange = sendAndWriteResponse(service, "Hello from mock");

        assertNotNull(exchange);
        assertNotNull(exchange.getExchangeId());
        assertEquals("Hello from mock", exchange.getContent());
        assertEquals("hello", exchange.getPrompt().getLastMessage().getContent());
        assertNotNull(exchange.getChatOptions());
    }

    @Test
    public void testContentAfterEofMarkerTrimmed() throws Exception {
        FileMockAiChatService service = new FileMockAiChatService(this);

        AiChatExchange exchange = sendAndWriteResponse(service, "part1\nNOP_EOF\npart2-ignored");

        assertEquals("part1", exchange.getContent());
    }

    @Test
    public void testCancelTokenCancels() throws Exception {
        FileMockAiChatService service = new FileMockAiChatService(this);

        AtomicBoolean cancelled = new AtomicBoolean(true);
        ICancelToken cancelToken = new ICancelToken() {
            @Override
            public boolean isCancelled() {
                return cancelled.get();
            }

            @Override
            public String getCancelReason() {
                return "test-cancel";
            }

            @Override
            public void appendOnCancel(java.util.function.Consumer<String> task) {
            }

            @Override
            public void removeOnCancel(java.util.function.Consumer<String> task) {
            }
        };

        assertThrows(CancellationException.class, () ->
                FutureHelper.syncGet(service.sendChatAsync(Prompt.userText("hi"), mockOptions(), cancelToken)));
    }
}
