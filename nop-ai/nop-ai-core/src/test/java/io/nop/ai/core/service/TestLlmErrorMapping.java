package io.nop.ai.core.service;

import io.nop.ai.api.chat.ErrorClassification;
import io.nop.ai.core.model.LlmErrorMappingModel;
import io.nop.ai.core.model.LlmErrorResponseModel;
import io.nop.ai.core.model.LlmModel;
import io.nop.autotest.junit.JunitBaseTestCase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * LLM 错误规范化配置加载测试（设计
 * {@code nop-ai-llm-error-normalization-design.md} §3.3 / 可行性分析
 * {@code ai-dev/analysis/2026-08/2026-08-01-llm-error-mapping-feasibility-analysis.md}）。
 *
 * <p>验证：①各 provider 的 {@code <errorResponse>} / {@code <errorMappings>} 配置正确加载；
 * ②first-match-wins 保序（同 classification 多条规则按声明顺序命中）；
 * ③OpenAI 429 双分类（rate_limit_exceeded → RATE_LIMITED vs insufficient_quota → QUOTA_EXCEEDED）。</p>
 */
public class TestLlmErrorMapping extends JunitBaseTestCase {

    @AfterEach
    public void tearDown() {
        LlmConfigHelper.reset();
    }

    @Test
    public void testOpenAiErrorResponseLoaded() {
        LlmModel model = LlmConfigHelper.loadConfig("deepseek"); // extends default
        LlmErrorResponseModel errorResponse = model.getErrorResponse();
        assertNotNull(errorResponse, "default.llm.xml 应加载 errorResponse");
        assertEquals("error.type", errorResponse.getErrorTypePath());
        assertEquals("error.code", errorResponse.getErrorCodePath());
        assertEquals("error.message", errorResponse.getErrorMessagePath());
    }

    @Test
    public void testOpenAiErrorMappingsLoaded() {
        LlmModel model = LlmConfigHelper.loadConfig("deepseek"); // extends default
        List<LlmErrorMappingModel> mappings = model.getErrorMappings();
        assertNotNull(mappings);
        assertFalse(mappings.isEmpty(), "default.llm.xml 应加载 errorMappings");
        assertEquals(ErrorClassification.QUOTA_EXCEEDED, mappings.get(0).getClassification(),
                "首条必须是 QUOTA_EXCEEDED（优先于通用 429 RATE_LIMITED）");
    }

    @Test
    public void testOpenAiQuotaBeforeRateLimitOrder() {
        // 验证 429 双分类的 first-match-wins 顺序：insufficient_quota 规则必须在 rate_limit_exceeded 之前
        LlmModel model = LlmConfigHelper.loadConfig("deepseek");
        List<LlmErrorMappingModel> mappings = model.getErrorMappings();
        int quotaIdx = -1, rateIdx = -1;
        for (int i = 0; i < mappings.size(); i++) {
            LlmErrorMappingModel m = mappings.get(i);
            if (m.getClassification() == ErrorClassification.QUOTA_EXCEEDED
                    && m.getErrorCodes() != null && m.getErrorCodes().contains("insufficient_quota"))
                quotaIdx = i;
            if (m.getClassification() == ErrorClassification.RATE_LIMITED
                    && m.getErrorCodes() != null && m.getErrorCodes().contains("rate_limit_exceeded"))
                rateIdx = i;
        }
        assertTrue(quotaIdx >= 0, "应存在 insufficient_quota → QUOTA_EXCEEDED 规则");
        assertTrue(rateIdx >= 0, "应存在 rate_limit_exceeded → RATE_LIMITED 规则");
        assertTrue(quotaIdx < rateIdx,
                "QUOTA_EXCEEDED 规则必须先于 RATE_LIMITED（first-match-wins 保证配额优先）");
    }

    @Test
    public void testClaudeAnthropicErrorMappingsLoaded() {
        LlmModel model = LlmConfigHelper.loadConfig("claude");
        List<LlmErrorMappingModel> mappings = model.getErrorMappings();
        assertNotNull(mappings);
        assertFalse(mappings.isEmpty());
        // Anthropic 402 billing_error → QUOTA_EXCEEDED（最干净来源，不依赖 body 解析）
        LlmErrorMappingModel first = mappings.get(0);
        assertEquals(ErrorClassification.QUOTA_EXCEEDED, first.getClassification());
        assertTrue(first.getHttpStatus().contains("402"));
        assertTrue(first.getErrorTypes().contains("billing_error"));
    }

    @Test
    public void testGeminiErrorMappingsWithMessagePattern() {
        LlmModel model = LlmConfigHelper.loadConfig("gemini");
        List<LlmErrorMappingModel> mappings = model.getErrorMappings();
        assertNotNull(mappings);
        assertFalse(mappings.isEmpty());
        // Gemini 429 RESOURCE_EXHAUSTED 同型，QUOTA 规则必须有 messagePattern 逃生舱
        LlmErrorMappingModel quota = mappings.get(0);
        assertEquals(ErrorClassification.QUOTA_EXCEEDED, quota.getClassification());
        assertTrue(quota.getErrorTypes().contains("RESOURCE_EXHAUSTED"));
        assertNotNull(quota.getMessagePattern(), "Gemini 配额规则必须带 messagePattern");
        assertTrue(quota.getMessagePattern().contains("quota"), "messagePattern 应匹配 quota 文案");
    }

    @Test
    public void testAzureNestedErrorCodePath() {
        LlmModel model = LlmConfigHelper.loadConfig("azure");
        LlmErrorResponseModel errorResponse = model.getErrorResponse();
        assertNotNull(errorResponse);
        assertEquals("error.inner_error.code", errorResponse.getErrorCodePath(),
                "Azure 嵌套错误码路径应加载");
        assertEquals("error.code", errorResponse.getErrorTypePath());
    }

    @Test
    public void testOllamaErrorMessagePath() {
        LlmModel model = LlmConfigHelper.loadConfig("ollama");
        LlmErrorResponseModel errorResponse = model.getErrorResponse();
        assertNotNull(errorResponse);
        assertEquals("error", errorResponse.getErrorMessagePath(),
                "Ollama 顶层 error 消息路径应加载");
    }

    @Test
    public void testNoErrorMappingProviderFallsBack() {
        // 未配置 errorMappings 的 provider（若存在）不报错 —— 默认启发式兜底
        LlmModel model = LlmConfigHelper.loadConfig("free"); // extends deepseek → default
        assertNotNull(model);
        // free extends deepseek extends default，应继承 OpenAI 映射
        assertFalse(model.getErrorMappings().isEmpty());
    }

    @Test
    public void testErrorMappingHasIds() {
        // 每个 errorMapping 必须有唯一 id（x:extends 合并键）
        LlmModel model = LlmConfigHelper.loadConfig("deepseek");
        List<LlmErrorMappingModel> mappings = model.getErrorMappings();
        for (LlmErrorMappingModel m : mappings) {
            assertNotNull(m.getId(), "errorMapping 必须有 id: " + m.getClassification());
        }
        assertEquals("openai-quota-exceeded", mappings.get(0).getId(),
                "首条必须是 openai-quota-exceeded（first-match-wins 顺序）");
    }

    @Test
    public void testAzureOverridesById() {
        // azure extends default：errorMapping 按 id 覆盖，而非整列表冲突
        LlmModel model = LlmConfigHelper.loadConfig("azure");
        List<LlmErrorMappingModel> mappings = model.getErrorMappings();
        assertNotNull(mappings);
        assertFalse(mappings.isEmpty());
        // azure 的 errorResponse 已替换（errorTypePath=error.code 而非 error.type）
        assertEquals("error.code", model.getErrorResponse().getErrorTypePath(),
                "azure errorResponse 应按 id/节点覆盖父配置");
        // azure 定义了自己的 errorMappings（x:extends 按 id 合并后）
        boolean hasAzureQuota = mappings.stream().anyMatch(
            m -> "azure-quota-exceeded".equals(m.getId()));
        assertTrue(hasAzureQuota, "azure 的 errorMapping 应存在");
    }
}
