package io.nop.ai.core.service;

import io.nop.ai.api.chat.ChatOptions;
import io.nop.ai.api.chat.ChatRequest;
import io.nop.ai.api.chat.ChatResponse;
import io.nop.ai.api.chat.messages.ChatAssistantMessage;
import io.nop.ai.core.api.chat.AiChatOptions;
import io.nop.ai.core.api.messages.AiChatExchange;
import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.core.resource.IResource;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestChatLogHelper extends JunitBaseTestCase {

    private static final String SECRET = "sk-abcdefghijklmnopqrstuvwxyz123";

    @Test
    public void testSessionResourcePathForChatRequest() {
        File dir = new File("target/test-log");
        ChatRequest request = ChatRequest.userPrompt("hello");
        ChatOptions options = new ChatOptions();
        options.setSessionId("sess-1");
        request.setOptions(options);
        request.setRequestTime(1000);
        request.setRequestId("req-1");
        request.setRetryTimes(2);

        IResource resource = ChatLogHelper.getSessionResource(dir.getPath(), request, "-request.yaml");

        assertTrue(resource.getPath().contains("/sess-1/1000-2-req-1-request.yaml"),
                "unexpected resource path: " + resource.getPath());
    }

    @Test
    public void testSessionResourceGeneratesSessionId() {
        ChatRequest request = ChatRequest.userPrompt("hello");
        request.setOptions(new ChatOptions());

        IResource resource = ChatLogHelper.getSessionResource("target/test-log", request, "-request.yaml");

        assertNotNull(request.getOptions().getSessionId());
        assertTrue(resource.getPath().contains("/" + request.getOptions().getSessionId() + "/"));
    }

    @Test
    public void testSessionResourceForExchange() {
        AiChatExchange exchange = new AiChatExchange();
        exchange.setExchangeId("exchange-1");
        exchange.setBeginTime(2000);

        IResource resource = ChatLogHelper.getSessionResource("target/test-log", exchange, "-response.md");

        assertTrue(resource.getPath().contains("/exchange-1/2000-response.md"),
                "unexpected resource path: " + resource.getPath());
    }

    @Test
    public void testSessionResourceForExchangeGeneratesId() {
        AiChatExchange exchange = new AiChatExchange();
        AiChatOptions options = new AiChatOptions();
        exchange.setChatOptions(options);

        IResource resource = ChatLogHelper.getSessionResource("target/test-log", exchange, "-prompt.md");

        assertNotNull(exchange.getExchangeId());
        assertTrue(resource.getPath().contains("/" + exchange.getExchangeId() + "/"));
    }

    @Test
    public void testDefaultChatLoggerRedactsCredentials() throws Exception {
        File logDir = File.createTempFile("ai-log", "");
        logDir.delete();
        logDir.mkdirs();
        logDir.deleteOnExit();

        DefaultChatLogger logger = new DefaultChatLogger();
        logger.setLogDir(logDir.getPath());
        logger.setRedactCredentials(true);
        assertTrue(logger.isValidLogDir());

        ChatRequest request = ChatRequest.userPrompt("hello api-key: " + SECRET);
        ChatOptions options = new ChatOptions();
        options.setSessionId("sess-redact");
        request.setOptions(options);

        logger.logRequest(request);

        String written = readFirstYaml(logDir, "request.yaml");
        assertNotNull(written);
        assertTrue(written.contains("***REDACTED***"), "credentials should be redacted");
        assertFalse(written.contains(SECRET), "raw secret must not appear in the log");
    }

    @Test
    public void testDefaultChatLoggerRedactsResponse() throws Exception {
        File logDir = File.createTempFile("ai-log", "");
        logDir.delete();
        logDir.mkdirs();
        logDir.deleteOnExit();

        DefaultChatLogger logger = new DefaultChatLogger();
        logger.setLogDir(logDir.getPath());
        logger.setRedactCredentials(true);

        ChatRequest request = ChatRequest.userPrompt("hello");
        request.setOptions(new ChatOptions());
        ChatResponse response = new ChatResponse(new ChatAssistantMessage("token: " + SECRET));

        logger.logResponse(request, response);

        String written = readFirstYaml(logDir, "response.yaml");
        assertNotNull(written);
        assertFalse(written.contains(SECRET), "raw secret must not appear in the response log");
    }

    @Test
    public void testDefaultChatLoggerInvalidLogDir() {
        DefaultChatLogger logger = new DefaultChatLogger();
        logger.setLogDir("none");
        assertFalse(logger.isValidLogDir());

        logger.setLogDir(null);
        assertFalse(logger.isValidLogDir());
    }

    private static String readFirstYaml(File logDir, String postfix) throws Exception {
        File[] yearDirs = logDir.listFiles();
        if (yearDirs == null)
            return null;
        for (File yearDir : yearDirs) {
            if (!yearDir.isDirectory() || yearDir.getName().length() != 4)
                continue;
            File[] monthDirs = yearDir.listFiles();
            if (monthDirs == null)
                continue;
            for (File monthDir : monthDirs) {
                if (!monthDir.isDirectory())
                    continue;
                File[] sessionDirs = monthDir.listFiles();
                if (sessionDirs == null)
                    continue;
                for (File sessionDir : sessionDirs) {
                    File[] files = sessionDir.listFiles(f -> f.getName().endsWith(postfix));
                    if (files != null && files.length > 0) {
                        return new String(Files.readAllBytes(files[0].toPath()), StandardCharsets.UTF_8);
                    }
                }
            }
        }
        return null;
    }

    @Test
    public void testMakeSessionIdDefault() {
        ChatRequest request = ChatRequest.userPrompt("hello");
        request.setOptions(new ChatOptions());
        String sessionId = ChatLogHelper.makeSessionId(request);
        assertNotNull(sessionId);
        assertEquals(sessionId, request.getOptions().getSessionId());
    }

    @Test
    public void testMakeSessionIdAcceptsValidCallerSessionId() {
        ChatRequest request = ChatRequest.userPrompt("hello");
        ChatOptions options = new ChatOptions();
        options.setSessionId("user-42_abc");
        request.setOptions(options);

        assertEquals("user-42_abc", ChatLogHelper.makeSessionId(request));
    }

    @Test
    public void testMakeSessionIdRejectsPathTraversal() {
        ChatRequest request = ChatRequest.userPrompt("hello");
        ChatOptions options = new ChatOptions();
        options.setSessionId("../etc/passwd");
        request.setOptions(options);

        assertThrows(IllegalArgumentException.class, () -> ChatLogHelper.makeSessionId(request),
                "path-traversal sessionId must be rejected (MA6.5-AR-9)");
    }

    @Test
    public void testMakeSessionIdRejectsSlashesAndDots() {
        assertThrows(IllegalArgumentException.class, () -> {
            ChatRequest request = ChatRequest.userPrompt("hello");
            ChatOptions options = new ChatOptions();
            options.setSessionId("a/b");
            request.setOptions(options);
            ChatLogHelper.makeSessionId(request);
        });
        assertThrows(IllegalArgumentException.class, () -> {
            ChatRequest request = ChatRequest.userPrompt("hello");
            ChatOptions options = new ChatOptions();
            options.setSessionId("..");
            request.setOptions(options);
            ChatLogHelper.makeSessionId(request);
        });
    }
}
