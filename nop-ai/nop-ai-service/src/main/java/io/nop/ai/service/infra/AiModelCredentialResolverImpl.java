/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.ai.service.infra;

import io.nop.ai.api.credential.IAiModelCredentialResolver;
import io.nop.ai.dao.entity.NopAiModel;
import io.nop.api.core.beans.FilterBeans;
import io.nop.api.core.beans.query.QueryBean;
import io.nop.api.core.exceptions.ErrorCode;
import io.nop.api.core.exceptions.NopException;
import jakarta.annotation.Nullable;
import io.nop.commons.util.StringHelper;
import io.nop.credential.api.ICredentialProvider;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * {@link IAiModelCredentialResolver} 实现（W7-successor，plan 2026-08-13-1118-3）。
 *
 * <p>把 {@code NopAiModel.credentialId} 接入 LLM 运行时调用链：按 {@code provider + modelName}
 * 查 {@code NopAiModel} 行取 {@code credentialId}，再经 {@link ICredentialProvider#getCredentialData}
 * 解析为明文 apiKey。
 *
 * <p><b>查找键/粒度（Phase 1 D4）</b>：{@code provider + modelName} 精确匹配 NopAiModel 注册。
 * 模型仅在 {@code .llm.xml} 存在而无 NopAiModel 行（身份不对应）= 显式回退 + WARN 审计（返回 null，
 * 调用方回退 resolveApiKey），非报错。
 *
 * <p><b>fail-closed（Phase 1 D5）</b>：credentialId 非空但凭证缺失/软删/解密失败 →
 * {@code ICredentialProvider.getCredentialData} 抛 {@link NopException}，本方法直接传播（不吞）。
 * 凭证存在但 {@code apiKey} 字段为空 → 抛 {@link #ERR_AI_CREDENTIAL_FIELD_EMPTY}（防凭证配错静默用错 key）。
 *
 * <p><b>明文边界</b>：解析出的明文 apiKey 仅返回给调用方（ChatServiceImpl）注入 dialect header，
 * 不回写 NopAiModel、不日志明文、不返回 GraphQL。
 *
 * <p>字段为 protected 以兼容 NopIoC 字段注入（AGENTS.md）。
 */
public class AiModelCredentialResolverImpl implements IAiModelCredentialResolver {

    private static final Logger LOG = LoggerFactory.getLogger(AiModelCredentialResolverImpl.class);

    /**
     * 凭证字段名：apiKey。对应 credential-type.xml（如 openai-api-key）声明的字段。
     */
    static final String FIELD_API_KEY = "apiKey";

    static final String ARG_PROVIDER = "provider";
    static final String ARG_MODEL = "model";
    static final String ARG_CREDENTIAL_ID = "credentialId";
    static final String ARG_FIELD = "field";

    /**
     * credentialId 非空且凭证存在，但解析出的字段值为空（凭证配错）。强 fail-closed。
     *
     * <p>Error-code ID follows the {@code nop.err.ai.*} dotted convention
     * (M5-P1 round-1 audit finding 2): the former uppercase constant-style ID
     * broke i18n/log/frontend resolution on the cross-module consumption path
     * (ChatServiceImpl); description is English per AGENTS.md.
     */
    static final ErrorCode ERR_AI_CREDENTIAL_FIELD_EMPTY = ErrorCode.define(
            "nop.err.ai.service.credential-field-empty",
            "AI credential field '{field}' is empty for credentialId '{credentialId}' (credential may be misconfigured; check the field name/value)",
            ARG_CREDENTIAL_ID, ARG_FIELD);

    /**
     * credentialId 非空但 nop-credential-service 未部署（{@link ICredentialProvider} bean 不可用）。
     * 强 fail-closed：配置了 credentialId 却没有凭证库，是部署不一致，不能静默用错 key。
     */
    static final ErrorCode ERR_AI_CREDENTIAL_PROVIDER_NOT_AVAILABLE = ErrorCode.define(
            "nop.err.ai.service.credential-provider-not-available",
            "AI model configured credentialId '{credentialId}' but ICredentialProvider is not deployed (add nop-credential-service and import credential-defaults.beans.xml)",
            ARG_CREDENTIAL_ID);

    /**
     * 数据访问。可选装配（{@code @Nullable} setter）：部署无 DB（如纯单元测试的全局容器初始化）时为 null，
     * resolver 调用时 fail-closed（{@link #ERR_AI_CREDENTIAL_RESOLVER_NOT_CONFIGURED}）。
     * 无字段 {@code @Inject}——仅经 setter 注入，避免 nop-credential 未部署时启动失败。
     */
    protected IDaoProvider daoProvider;

    /**
     * 凭证消费 SPI（可选装配）。无 {@code @Inject} 字段注解——仅经 {@link #setCredentialProvider} 的
     * {@code @Nullable} setter 注入（NopIoC optional），避免部署不含 nop-credential 时启动失败。
     * 为 null 时：credentialId 空 → 正常回退；credentialId 非空 → 强 fail-closed（部署不一致）。
     */
    protected ICredentialProvider credentialProvider;

    /**
     * resolver 未就绪（daoProvider 未装配，如部署无 DB）。强 fail-closed。
     */
    static final ErrorCode ERR_AI_CREDENTIAL_RESOLVER_NOT_CONFIGURED = ErrorCode.define(
            "nop.err.ai.service.credential-resolver-not-configured",
            "AI credential resolver is not ready (IDaoProvider not wired); cannot query NopAiModel.credentialId");

    @Inject
    public void setDaoProvider(@Nullable IDaoProvider daoProvider) {
        this.daoProvider = daoProvider;
    }

    /**
     * 可选注入（NopIoC：{@code @Nullable} → optional=true）。
     */
    @Inject
    public void setCredentialProvider(@Nullable ICredentialProvider credentialProvider) {
        this.credentialProvider = credentialProvider;
    }

    @Override
    public String resolveApiKeyByCredential(String provider, String model) {
        if (StringHelper.isEmpty(provider) || StringHelper.isEmpty(model)) {
            return null;
        }

        String credentialId = findCredentialId(provider, model);
        if (StringHelper.isEmpty(credentialId)) {
            // 模型未配 credentialId（credentialId 为空），或无 NopAiModel 行（身份不对应）。
            // 正常兼容路径：返回 null，调用方回退 resolveApiKey（Phase 1 D4）。
            return null;
        }

        // credentialId 非空 → 必须解析出非空 apiKey，否则强 fail-closed（Phase 1 D5）。
        if (credentialProvider == null) {
            // 部署不一致：配了 credentialId 但凭证库未部署。强 fail-closed，不静默回退到 config 变量。
            throw new NopException(ERR_AI_CREDENTIAL_PROVIDER_NOT_AVAILABLE)
                    .param(ARG_CREDENTIAL_ID, credentialId);
        }
        // ICredentialProvider.getCredentialData 对缺失/软删/解密失败已抛 NopException，此处直接传播。
        Object apiKeyValue = credentialProvider.getCredentialData(credentialId, FIELD_API_KEY);
        // D6-04（A1-audit successor，2026-08-17）：isBlank 判空——纯空白 apiKey 字段值
        // 视同配错凭证，不得当有效 key 使用（fail-closed）
        if (apiKeyValue == null || StringHelper.isBlank(apiKeyValue.toString())) {
            throw new NopException(ERR_AI_CREDENTIAL_FIELD_EMPTY)
                    .param(ARG_CREDENTIAL_ID, credentialId)
                    .param(ARG_FIELD, FIELD_API_KEY);
        }
        return apiKeyValue.toString();
    }

    /**
     * 按 {@code provider + modelName} 查 NopAiModel 行的 credentialId。
     * <p>
     * 无匹配行（模型仅在 {@code .llm.xml} 存在而无注册表行）= 身份不对应，记 WARN 审计并返回 null
     * （显式回退，Phase 1 D4）。credentialId 列为空也返回 null（模型未配凭证）。
     * 查询用 {@code setLimit(1)}；同一 provider+modelName 多行属于数据异常，取首行并 WARN。
     */
    private String findCredentialId(String provider, String model) {
        if (daoProvider == null) {
            // resolver bean 存在但未装配 IDaoProvider（如部署无 DB）—— fail-closed，不静默回退。
            throw new NopException(ERR_AI_CREDENTIAL_RESOLVER_NOT_CONFIGURED);
        }
        IEntityDao<NopAiModel> dao = daoProvider.daoFor(NopAiModel.class);
        QueryBean query = new QueryBean();
        query.addFilter(FilterBeans.eq(NopAiModel.PROP_NAME_provider, provider));
        query.addFilter(FilterBeans.eq(NopAiModel.PROP_NAME_modelName, model));
        query.setLimit(2);
        List<NopAiModel> rows = dao.findAllByQuery(query);

        if (rows.isEmpty()) {
            LOG.warn("AiModel credential lookup: no NopAiModel row for provider='{}', model='{}' "
                    + "(model may exist only in .llm.xml without a registry row); "
                    + "falling back to config-var/secret apiKey", provider, model);
            return null;
        }
        if (rows.size() > 1) {
            LOG.warn("AiModel credential lookup: multiple NopAiModel rows for provider='{}', "
                    + "model='{}' (count={}); using the first row's credentialId", provider, model, rows.size());
        }
        return rows.get(0).getCredentialId();
    }
}
