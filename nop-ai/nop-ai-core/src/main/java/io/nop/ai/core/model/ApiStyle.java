package io.nop.ai.core.model;

/**
 * API 风格枚举，对应不同 AI 提供商的请求/响应格式。
 * <p>
 * 参考 solon-ai 的 dialect 概念，但使用配置驱动的方式实现。
 * 不同风格的差异主要在：
 * <ul>
 *   <li>请求体结构（messages vs contents）</li>
 *   <li>System 消息处理方式</li>
 *   <li>响应路径结构</li>
 *   <li>工具调用格式</li>
 * </ul>
 */
public enum ApiStyle {
    /**
     * OpenAI 标准格式（默认）
     * - messages 数组包含 system/user/assistant
     * - 响应路径: choices[0].message.content
     */
    openai,

    /**
     * Ollama 本地部署格式
     * - 参数嵌套在 options 对象中
     * - 响应路径: message.content
     */
    ollama,

    /**
     * Anthropic Claude 格式
     * - system 消息单独放在 system 字段
     * - 消息 content 使用数组结构
     * - 响应路径: content[0].text
     * - 工具调用: content[].tool_use
     */
    anthropic,

    /**
     * Google Gemini 格式
     * - 使用 contents 数组而非 messages
     * - system 消息使用 systemInstruction 字段
     * - 参数在 generationConfig 中
     * - 响应路径: candidates[0].content.parts[0].text
     */
    gemini,

    /**
     * 其他/自定义格式
     * - 通过 XPL 函数 buildHttpRequest/parseHttpResponse 处理
     */
    other
}