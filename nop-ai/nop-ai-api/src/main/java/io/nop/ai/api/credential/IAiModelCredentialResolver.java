/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.ai.api.credential;

/**
 * AI 模型凭证解析 SPI（W7-successor）。把 {@code NopAiModel.credentialId} 接入 LLM 运行时调用链：
 * 按 {@code provider + modelName} 查 {@code NopAiModel} 行取 {@code credentialId}，再经
 * {@code ICredentialProvider.getCredentialData(id,"apiKey")} 解析为明文 apiKey。
 *
 * <p><b>集成层裁决（plan 2026-08-13-1118-3 Phase 1 D1）</b>：本接口位于 {@code nop-ai-api}（跨模块
 * SPI 载体），impl 位于 {@code nop-ai-service}（持 nop-ai-dao + nop-credential-api），钩点为
 * {@code ChatServiceImpl.buildHttpRequest}。{@code nop-ai-core} 不直接依赖 DAO/credential，
 * 故经此可注入抽象解耦。
 *
 * <p><b>apiKey 优先级链（Phase 1 D2）</b>：{@code accountKey > credentialId > resolveApiKey}。
 * 本 resolver 仅在 accountKey 为空时被 consult。
 *
 * <p><b>返回语义</b>：
 * <ul>
 *   <li>返回非空 String = 该模型配了 credentialId 且凭证库成功解析出 apiKey，调用方应使用之。</li>
 *   <li>返回 null = 该模型未配 credentialId（正常兼容路径），调用方回退 {@code resolveApiKey}。</li>
 * </ul>
 *
 * <p><b>fail-closed（Phase 1 D5）</b>：当 {@code credentialId} 非空但凭证缺失/软删/解密失败/字段为空时，
 * 本方法抛出 {@link io.nop.api.core.exceptions.NopException}（强 fail-closed，不静默回退到 config 变量）。
 */
public interface IAiModelCredentialResolver {

    /**
     * 按 {@code provider + model} 解析该模型的 credentialId 对应的明文 apiKey。
     *
     * @param provider LLM provider 名（对应 {@code NopAiModel.provider}）
     * @param model    模型名（对应 {@code NopAiModel.modelName}，即 {@code .llm.xml} 解析后的 model 名）
     * @return 明文 apiKey（配了 credentialId 且解析成功）；null 表示未配 credentialId（调用方回退）
     * @throws io.nop.api.core.exceptions.NopException credentialId 非空但凭证失效（缺失/软删/解密失败/字段空）
     */
    String resolveApiKeyByCredential(String provider, String model);
}
