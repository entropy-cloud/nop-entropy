package io.nop.ai.core.service;

import io.nop.ai.api.chat.ChatOptions;
import io.nop.ai.core.model.LlmModel;
import io.nop.ai.core.model.LlmModelModel;
import io.nop.api.core.config.AppConfig;
import io.nop.api.core.exceptions.NopException;
import io.nop.autotest.junit.JunitBaseTestCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static io.nop.ai.core.AiCoreConfigs.CFG_AI_SERVICE_DEFAULT_LLM;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class TestLlmConfigHelper extends JunitBaseTestCase {

    @BeforeEach
    void resetStaticState() {
        // MA6.1-AR-6: static secretCache/secretDir must not leak across tests.
        LlmConfigHelper.reset();
    }

    @Test
    public void testLoadConfig() {
        LlmModel model = LlmConfigHelper.loadConfig("deepseek");
        assertNotNull(model);
        assertEquals("https://api.deepseek.com", model.getBaseUrl());
        assertNotNull(model.getModel("deepseek-chat"));
        assertEquals(8192, model.getModel("deepseek-chat").getMaxTokensLimit());
    }

    @Test
    public void testGetProvider() {
        ChatOptions options = new ChatOptions();
        options.setProvider("ollama");
        assertEquals("ollama", LlmConfigHelper.getProvider(options));

        String oldDefault = CFG_AI_SERVICE_DEFAULT_LLM.get();
        try {
            AppConfig.getConfigProvider().updateConfigValue(CFG_AI_SERVICE_DEFAULT_LLM, "deepseek");
            ChatOptions empty = new ChatOptions();
            assertEquals("deepseek", LlmConfigHelper.getProvider(empty));
        } finally {
            AppConfig.getConfigProvider().updateConfigValue(CFG_AI_SERVICE_DEFAULT_LLM, oldDefault);
        }
    }

    @Test
    public void testResolveModelWithAlias() {
        LlmModel config = LlmConfigHelper.loadConfig("volcengine");
        assertEquals("deepseek-chat", config.getDefaultModel());

        ChatOptions noModel = new ChatOptions();
        assertEquals("deepseek-v3-250324", LlmConfigHelper.resolveModel(config, noModel));

        ChatOptions alias = new ChatOptions();
        alias.setModel("doubao-1.5");
        assertEquals("doubao-1-5-pro-32k-250115", LlmConfigHelper.resolveModel(config, alias));
    }

    @Test
    public void testResolveModelWithoutDefaultFails() {
        LlmModel config = new LlmModel();
        assertThrows(NopException.class, () -> LlmConfigHelper.resolveModel(config, new ChatOptions()));
    }

    @Test
    public void testGetModelConfigBaseNameFallback() {
        LlmModel config = LlmConfigHelper.loadConfig("ollama");
        assertNotNull(config.getModel("qwen3"));

        LlmModelModel model = LlmConfigHelper.getModelConfig(config, "qwen3:14b");
        assertNotNull(model, "base name fallback should resolve qwen3 for qwen3:14b");
        assertEquals("qwen3", model.getName());
    }

    @Test
    public void testGetModelConfigUnknownReturnsNull() {
        LlmModel config = LlmConfigHelper.loadConfig("ollama");
        assertNull(LlmConfigHelper.getModelConfig(config, "not-exist"));
    }

    @Test
    public void testResolveApiKeyFromConfig() {
        String key = "nop.ai.llm.deepseek.api-key";
        Object old = AppConfig.var(key);
        try {
            AppConfig.getConfigProvider().assignConfigValue(key, "sk-test-123");
            LlmConfigHelper.clearSecretCache();
            assertEquals("sk-test-123", LlmConfigHelper.resolveApiKey("deepseek"));
        } finally {
            AppConfig.getConfigProvider().assignConfigValue(key, old);
            LlmConfigHelper.clearSecretCache();
        }
    }

    @Test
    public void testResolveApiKeyFromSecretFile() throws Exception {
        File dir = File.createTempFile("ai-secret", "");
        dir.delete();
        dir.mkdirs();
        File secretFile = new File(dir, "deepseek.txt");
        Files.write(secretFile.toPath(), "sk-from-file".getBytes(StandardCharsets.UTF_8));

        try {
            LlmConfigHelper.setSecretDir(dir);
            LlmConfigHelper.clearSecretCache();
            assertEquals("sk-from-file", LlmConfigHelper.resolveApiKey("deepseek"));
        } finally {
            LlmConfigHelper.setSecretDir(null);
            LlmConfigHelper.clearSecretCache();
            secretFile.delete();
            dir.delete();
        }
    }

    @Test
    public void testResetClearsCacheAndDir() throws Exception {
        // MA6.1-AR-6: reset() must clear BOTH the cache and the secretDir, so
        // a stale cached secret never survives into a new secret directory.
        String key = "nop.ai.llm.deepseek.api-key";
        AppConfig.getConfigProvider().assignConfigValue(key, null);
        LlmConfigHelper.reset();

        File dirA = File.createTempFile("ai-secret-a", "");
        dirA.delete();
        dirA.mkdirs();
        File fileA = new File(dirA, "deepseek.txt");
        Files.write(fileA.toPath(), "sk-A".getBytes(StandardCharsets.UTF_8));

        File dirB = File.createTempFile("ai-secret-b", "");
        dirB.delete();
        dirB.mkdirs();
        File fileB = new File(dirB, "deepseek.txt");
        Files.write(fileB.toPath(), "sk-B".getBytes(StandardCharsets.UTF_8));

        try {
            // Populate the cache from dirA.
            LlmConfigHelper.setSecretDir(dirA);
            assertEquals("sk-A", LlmConfigHelper.resolveApiKey("deepseek"));

            // reset() clears cache + dir. With no dir and no config var, the
            // next resolve is empty (the cached sk-A must not survive).
            LlmConfigHelper.reset();
            AppConfig.getConfigProvider().assignConfigValue(key, null);
            assertNull(LlmConfigHelper.resolveApiKey("deepseek"),
                    "After reset() (no dir, no config) resolveApiKey must be empty");

            // A new secret dir must take effect — the cleared cache must not
            // return the stale sk-A.
            LlmConfigHelper.setSecretDir(dirB);
            assertEquals("sk-B", LlmConfigHelper.resolveApiKey("deepseek"),
                    "reset() must clear the cache: the new secret dir's value wins");
        } finally {
            AppConfig.getConfigProvider().assignConfigValue(key, null);
            LlmConfigHelper.reset();
            fileA.delete();
            dirA.delete();
            fileB.delete();
            dirB.delete();
        }
    }
}
