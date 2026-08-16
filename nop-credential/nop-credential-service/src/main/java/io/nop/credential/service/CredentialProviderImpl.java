/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.credential.service;

import io.nop.api.core.annotations.txn.TransactionPropagation;
import io.nop.api.core.beans.FilterBeans;
import io.nop.api.core.beans.query.QueryBean;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.json.JSON;
import io.nop.commons.util.StringHelper;
import io.nop.core.lang.json.JsonTool;
import io.nop.credential.api.CredentialData;
import io.nop.credential.api.ICredentialProvider;
import io.nop.credential.api.MaskedCredential;
import io.nop.credential.api.TestResult;
import io.nop.credential.api.registry.CredentialType;
import io.nop.credential.api.registry.ICredentialTypeRegistry;
import io.nop.credential.config.CredentialConfigs;
import io.nop.credential.crypto.CredentialCipher;
import io.nop.credential.crypto.CredentialErrors;
import io.nop.credential.dao.entity.NopCredential;
import io.nop.credential.dao.entity.NopCredentialUsage;
import io.nop.credential.service.oauth.OAuthTokenClient;
import io.nop.credential.service.oauth.OAuthTokenResponse;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import io.nop.dao.txn.ITransactionTemplate;
import io.nop.orm.IOrmTemplate;
import jakarta.inject.Inject;

import java.sql.Timestamp;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * {@link ICredentialProvider} 的实现。平台内<strong>唯一的凭证解密点</strong>：
 * 所有 {@code cv1:} 密文在此处经 {@link CredentialCipher} 解密为明文，明文不跨出服务进程。
 *
 * <p>失败语义全部 fail-closed（Rule #24）：
 * <ul>
 *   <li>凭证不存在 → 抛 {@code ERR_CREDENTIAL_NOT_FOUND}</li>
 *   <li>凭证已软删除 → 抛 {@code ERR_CREDENTIAL_DELETED}（getCredential/getCredentialData/mask）</li>
 *   <li>解密失败 → 由 {@link CredentialCipher} 抛出（篡改/未知 keyId）</li>
 * </ul>
 *
 * <p><b>W9 引擎内部通道</b>（public 方法 + 引擎专用语义，供 {@code service.oauth} 包的 OAuth
 * 引擎调用——唯一解密点不变式：引擎类不得直接持有 {@link CredentialCipher} 解用户 data）：
 * <ul>
 *   <li>{@link #engineGetDecryptedFields(String)}：校验（存在/未删/oauth2 未禁用）+ 解密。</li>
 *   <li>{@link #engineUpdateTokenFields(String, Map)}：DB 行级锁（SELECT FOR UPDATE）下合并
 *       写引擎保留字段（只写保留名，人工字段不动）。</li>
 *   <li>{@link #engineUpdateInLock(String, Function)}：通用行锁写通道（Phase 3 惰性刷新与
 *       saveCredential 分组写共用同一串行化入口，设计 §3.3"同一凭证写路径串行化"）。</li>
 * </ul>
 */
public class CredentialProviderImpl implements ICredentialProvider {

    @Inject
    protected IDaoProvider daoProvider;

    @Inject
    protected CredentialCipher credentialCipher;

    @Inject
    protected ICredentialTypeRegistry credentialTypeRegistry;

    /**
     * 行级锁写通道所需的 ORM 会话模板（NopIoC 注入，字段 protected 兼容字段注入）。
     */
    @Inject
    protected IOrmTemplate ormTemplate;

    @Inject
    protected ITransactionTemplate txnTemplate;

    /**
     * OAuth 令牌端点协议客户端（惰性刷新外呼用；无 provider 反向依赖，无循环）。
     */
    @Inject
    protected OAuthTokenClient oauthTokenClient;

    public void setDaoProvider(IDaoProvider daoProvider) {
        this.daoProvider = daoProvider;
    }

    public void setCredentialCipher(CredentialCipher credentialCipher) {
        this.credentialCipher = credentialCipher;
    }

    public void setCredentialTypeRegistry(ICredentialTypeRegistry credentialTypeRegistry) {
        this.credentialTypeRegistry = credentialTypeRegistry;
    }

    public void setOrmTemplate(IOrmTemplate ormTemplate) {
        this.ormTemplate = ormTemplate;
    }

    public void setTxnTemplate(ITransactionTemplate txnTemplate) {
        this.txnTemplate = txnTemplate;
    }

    public void setOauthTokenClient(OAuthTokenClient oauthTokenClient) {
        this.oauthTokenClient = oauthTokenClient;
    }

    @Override
    public CredentialData getCredential(String credentialId) {
        NopCredential entity = loadActiveCredential(credentialId);
        Map<String, Object> fields = decryptToData(entity).getFields();

        // W9 惰性刷新：oauth2 类型 accessToken 临期 → 行锁互斥下先刷新再返回明文（设计 §3.3）
        CredentialType type = resolveType(entity.getTypeName());
        if (type != null && type.isOauth2Type()) {
            fields = refreshIfNearingExpiry(credentialId, type, fields);
        }
        return new CredentialData(fields);
    }

    @Override
    public Object getCredentialData(String credentialId, String field) {
        CredentialData data = getCredential(credentialId);
        return data.getField(field);
    }

    @Override
    public TestResult testCredential(String credentialId) {
        NopCredential entity = loadActiveCredential(credentialId);

        TestResult result = new TestResult(false,
                "test not implemented for this credential type",
                new Timestamp(System.currentTimeMillis()));

        entity.setTestResult(resultToString(result));
        IEntityDao<NopCredential> dao = daoProvider.daoFor(NopCredential.class);
        dao.updateEntityDirectly(entity);

        return result;
    }

    @Override
    public MaskedCredential mask(String credentialId) {
        NopCredential entity = loadActiveCredential(credentialId);
        CredentialData data = decryptToData(entity);

        Map<String, String> masked = new LinkedHashMap<>();
        CredentialType type = null;
        try {
            type = credentialTypeRegistry.getType(entity.getTypeName());
        } catch (NopException ignored) {
            // 未知类型时将所有字段视为敏感（保守策略）
        }

        for (Map.Entry<String, Object> entry : data.getFields().entrySet()) {
            String name = entry.getKey();
            String strValue = entry.getValue() == null ? null : String.valueOf(entry.getValue());

            boolean sensitive = true;
            if (type != null) {
                sensitive = type.getFields().stream()
                        .filter(f -> f.getName().equals(name))
                        .map(CredentialType.CredentialField::isSensitive)
                        .findFirst().orElse(true);
            }

            masked.put(name, maskValue(strValue, sensitive));
        }

        return new MaskedCredential(masked);
    }

    @Override
    public void registerUsage(String credentialId, String consumerRef) {
        IEntityDao<NopCredentialUsage> dao = daoProvider.daoFor(NopCredentialUsage.class);

        QueryBean query = new QueryBean();
        query.addFilter(FilterBeans.eq("credentialId", credentialId));
        query.addFilter(FilterBeans.eq("consumerRef", consumerRef));
        List<NopCredentialUsage> existing = dao.findAllByQuery(query);

        if (!existing.isEmpty()) {
            return;
        }

        NopCredentialUsage usage = dao.newEntity();
        usage.setUsageId(StringHelper.generateUUID());
        usage.setCredentialId(credentialId);
        usage.setConsumerRef(consumerRef);
        usage.setCreateTime(new Timestamp(System.currentTimeMillis()));
        dao.saveEntityDirectly(usage);
    }

    @Override
    public void unregisterUsage(String credentialId, String consumerRef) {
        IEntityDao<NopCredentialUsage> dao = daoProvider.daoFor(NopCredentialUsage.class);

        QueryBean query = new QueryBean();
        query.addFilter(FilterBeans.eq("credentialId", credentialId));
        query.addFilter(FilterBeans.eq("consumerRef", consumerRef));
        List<NopCredentialUsage> existing = dao.findAllByQuery(query);

        for (NopCredentialUsage usage : existing) {
            dao.deleteEntityDirectly(usage);
        }
    }

    public long countUsage(String credentialId) {
        IEntityDao<NopCredentialUsage> dao = daoProvider.daoFor(NopCredentialUsage.class);
        QueryBean query = new QueryBean();
        query.addFilter(FilterBeans.eq("credentialId", credentialId));
        return dao.findAllByQuery(query).size();
    }

    // ==================== W9 引擎内部通道（唯一解密点不变式） ====================

    /**
     * <b>引擎专用</b>（service.oauth 包 OAuth 引擎调用，非 SPI 面）：校验（存在 / 未删 /
     * oauth2 类型未禁用）后解密返回完整明文字段 map。
     *
     * <p>本方法与 {@link #getCredential(String)} 的差异：oauth2 类型额外校验
     * {@code status=disabled}（发起/回调路径全路径拒绝，设计 §3.5 显式增量）。
     * 非 oauth2 类型维持一期语义（仅 delFlag）。
     */
    public Map<String, Object> engineGetDecryptedFields(String credentialId) {
        NopCredential entity = loadActiveCredential(credentialId);
        assertOauth2NotDisabled(entity);
        return decryptToData(entity).getFields();
    }

    /**
     * <b>引擎专用</b>：DB 行级锁（SELECT FOR UPDATE，事务模板内）下合并写引擎保留字段。
     *
     * <p>只写保留名（accessToken/refreshToken/expiresAt/tokenType/scope）——输入出现非保留名
     * 直接抛错（fail-closed）；保留名缺失的键保持现值（如提供方不轮换 refresh_token 时保留旧值）。
     * 人工字段整包不动。
     */
    public Map<String, Object> engineUpdateTokenFields(String credentialId, Map<String, Object> tokenFields) {
        for (String name : tokenFields.keySet()) {
            if (!CredentialType.OAUTH_RESERVED_FIELD_NAMES.contains(name)) {
                throw new NopException(CredentialErrors.ERR_CREDENTIAL_RESERVED_FIELD_INPUT)
                        .param(CredentialErrors.ARG_FIELD_NAMES, tokenFields.keySet());
            }
        }
        return engineUpdateInLock(credentialId, current -> {
            Map<String, Object> merged = new LinkedHashMap<>(current);
            for (Map.Entry<String, Object> entry : tokenFields.entrySet()) {
                if (entry.getValue() != null) {
                    merged.put(entry.getKey(), entry.getValue());
                }
            }
            return merged;
        });
    }

    /**
     * <b>引擎专用</b>：通用行锁写通道——事务模板（REQUIRED）内 SELECT FOR UPDATE 该凭证行，
     * 驱逐会话缓存后从 DB 重读、解密，应用 {@code updater}（可为空返回值语义之外的任意
     * 外呼，如惰性刷新的 token endpoint 调用），重加密回写。返回更新后的完整字段 map。
     *
     * <p>跨副本互斥语义（Phase 3 Decision）：同一凭证的"刷新 vs 刷新"与"刷新 vs 人工保存"
     * 均经由本入口串行化；持锁期间含一次秒级 HTTP 外呼为已接受的吞吐代价（单凭证粒度）。
     * updater 抛错 → 事务回滚，data 不变。
     */
    public Map<String, Object> engineUpdateInLock(String credentialId,
                                                  Function<Map<String, Object>, Map<String, Object>> updater) {
        return engineUpdateInLock(credentialId, updater, null);
    }

    /**
     * <b>引擎专用</b>：行锁写通道扩展——{@code entityCustomizer} 在锁下对已重读的实体设置
     * 元数据列（name/typeName/updateTime 等），与 data 重加密在<b>同一 UPDATE</b> 内提交
     * （避免"元数据先写 + data 后写"两步间乐观锁版本竞争导致的 update-entity-not-found
     * / 双写丢失）。customizer 为 null 时只写 data。
     */
    public Map<String, Object> engineUpdateInLock(String credentialId,
                                                  Function<Map<String, Object>, Map<String, Object>> updater,
                                                  BiConsumer<NopCredential, Map<String, Object>> entityCustomizer) {
        return ormTemplate.runInSession(session ->
                txnTemplate.runInTransaction(null, TransactionPropagation.REQUIRED, txn -> {
                    IEntityDao<NopCredential> dao = daoProvider.daoFor(NopCredential.class);
                    NopCredential probe = dao.getEntityById(credentialId);
                    if (probe == null) {
                        throw new NopException(CredentialErrors.ERR_CREDENTIAL_NOT_FOUND)
                                .param(CredentialErrors.ARG_CREDENTIAL_ID, credentialId);
                    }
                    if (isDeleted(probe)) {
                        throw new NopException(CredentialErrors.ERR_CREDENTIAL_DELETED)
                                .param(CredentialErrors.ARG_CREDENTIAL_ID, credentialId);
                    }
                    // 驱逐会话缓存中的旧副本，确保锁下从 DB 重读最新 data（防 fast-path 读入的陈旧缓存）
                    session.evict(probe);
                    NopCredential entity = dao.loadEntityById(credentialId);
                    dao.lockEntity(entity);

                    Map<String, Object> current = decryptToData(entity).getFields();
                    Map<String, Object> updated = updater.apply(current);

                    entity.setData(credentialCipher.encrypt(JsonTool.stringify(updated)));
                    entity.setUpdateTime(new Timestamp(System.currentTimeMillis()));
                    if (entityCustomizer != null) {
                        entityCustomizer.accept(entity, updated);
                    }
                    dao.updateEntityDirectly(entity);
                    return updated;
                }));
    }

    /**
     * oauth2 类型禁用拒绝（engine 通道用；Phase 3 接入 loadActiveCredential 全路径）。
     * 类型未注册时容忍（保持一期语义，不因新增校验破坏存量未知类型凭证取用）。
     */
    private void assertOauth2NotDisabled(NopCredential entity) {
        CredentialType type = resolveType(entity.getTypeName());
        if (type != null && type.isOauth2Type() && STATUS_DISABLED.equals(entity.getStatus())) {
            throw new NopException(CredentialErrors.ERR_CREDENTIAL_DISABLED)
                    .param(CredentialErrors.ARG_CREDENTIAL_ID, entity.getCredentialId());
        }
    }

    private static final String STATUS_DISABLED = "disabled";

    /**
     * 解析凭证类型；未注册返回 null（调用方按非 oauth2 处理，保持一期语义）。
     */
    private CredentialType resolveType(String typeName) {
        try {
            return credentialTypeRegistry.getType(typeName);
        } catch (NopException ignored) {
            return null;
        }
    }

    // ==================== W9 惰性刷新（跨副本互斥 = DB 行级锁 + 事务模板） ====================

    /**
     * 取用时惰性刷新（设计 §3.3）：oauth2 类型 accessToken 临期（now 距 expiresAt 小于刷新窗口）
     * → 行锁互斥下以 refreshToken + clientId/clientSecret 刷新 → 新 token 集回写（只写保留字段）
     * → 返回新明文。刷新失败（invalid_grant 等）fail-closed 抛错；refreshToken 缺失且已过期 →
     * fail-closed（提示重新授权）。
     *
     * <p>非临期直接返回（不持锁，非刷新取用可并发）；锁下双重检查保证并发取用同一凭证时
     * 刷新收敛为一次（跨副本经 DB 行锁 SELECT FOR UPDATE 互斥，Phase 3 Decision）。
     */
    private Map<String, Object> refreshIfNearingExpiry(String credentialId, CredentialType type,
                                                       Map<String, Object> fields) {
        Long expiresAt = asEpochMillis(fields.get("expiresAt"));
        if (expiresAt == null) {
            return fields; // 无 expiresAt（token 未写入或提供方未返回 expires_in）：无从判定期限
        }
        long windowMs = refreshWindowSeconds(type) * 1000L;
        long now = System.currentTimeMillis();
        if (now < expiresAt - windowMs) {
            return fields; // 非临期：直接返回，不持锁
        }

        // 临期：进入行锁互斥路径（锁下双重检查——并发下后到者直接读到先行者刷新后的 token）
        return engineUpdateInLock(credentialId, current -> {
            Long curExpiresAt = asEpochMillis(current.get("expiresAt"));
            if (curExpiresAt == null) {
                return current;
            }
            long nowInLock = System.currentTimeMillis();
            if (nowInLock < curExpiresAt - refreshWindowSeconds(type) * 1000L) {
                return current; // 已被并发先行者刷新，不再临期
            }

            String refreshToken = (String) current.get("refreshToken");
            if (StringHelper.isEmpty(refreshToken)) {
                if (curExpiresAt <= nowInLock) {
                    // accessToken 已过期且无 refreshToken → fail-closed（提示重新授权）
                    throw new NopException(CredentialErrors.ERR_CREDENTIAL_OAUTH_REAUTH_REQUIRED)
                            .param(CredentialErrors.ARG_CREDENTIAL_ID, credentialId);
                }
                return current; // 尚未过期、无 refreshToken：直接返回（下次取用到过期点再 fail-closed）
            }

            String clientId = (String) current.get("clientId");
            String clientSecret = (String) current.get("clientSecret");
            if (StringHelper.isEmpty(clientId) || StringHelper.isEmpty(clientSecret)) {
                throw new NopException(CredentialErrors.ERR_CREDENTIAL_OAUTH_REFRESH_FAILED)
                        .param(CredentialErrors.ARG_CREDENTIAL_ID, credentialId)
                        .param(CredentialErrors.ARG_ERROR, "clientId/clientSecret missing");
            }

            OAuthTokenResponse response;
            try {
                response = oauthTokenClient.refresh(type.getOauth2().getTokenEndpoint(),
                        clientId, clientSecret, refreshToken);
            } catch (NopException e) {
                // 刷新失败（invalid_grant 等）fail-closed：不静默使用旧 token、不静默返回空值
                throw new NopException(CredentialErrors.ERR_CREDENTIAL_OAUTH_REFRESH_FAILED, e)
                        .param(CredentialErrors.ARG_CREDENTIAL_ID, credentialId)
                        .param(CredentialErrors.ARG_ERROR, e.getMessage());
            }

            long nowAfterRefresh = System.currentTimeMillis();
            Map<String, Object> merged = new LinkedHashMap<>(current);
            merged.put("accessToken", response.getAccessToken());
            if (response.getRefreshToken() != null) {
                merged.put("refreshToken", response.getRefreshToken());
            }
            if (response.getExpiresIn() != null) {
                merged.put("expiresAt", nowAfterRefresh + response.getExpiresIn() * 1000L);
            }
            if (response.getTokenType() != null) {
                merged.put("tokenType", response.getTokenType());
            }
            if (response.getScope() != null) {
                merged.put("scope", response.getScope());
            }
            return merged;
        });
    }

    /**
     * 刷新窗口（秒）：类型 oauth2 元数据 refreshWindowSeconds 覆盖全局缺省
     * （nop.credential.oauth.refresh-window-seconds，缺省 300）。
     */
    private static long refreshWindowSeconds(CredentialType type) {
        Integer typeWindow = type.getOauth2() != null ? type.getOauth2().getRefreshWindowSeconds() : null;
        if (typeWindow != null && typeWindow > 0) {
            return typeWindow;
        }
        Integer global = CredentialConfigs.CFG_CREDENTIAL_OAUTH_REFRESH_WINDOW_SECONDS.get();
        return global != null && global > 0 ? global : DEFAULT_REFRESH_WINDOW_SECONDS;
    }

    private static final long DEFAULT_REFRESH_WINDOW_SECONDS = 300;

    private static Long asEpochMillis(Object value) {
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        return null;
    }

    private NopCredential loadActiveCredential(String credentialId) {
        if (credentialId == null) {
            throw new NopException(CredentialErrors.ERR_CREDENTIAL_NOT_FOUND)
                    .param(CredentialErrors.ARG_CREDENTIAL_ID, credentialId);
        }

        NopCredential entity = daoProvider.daoFor(NopCredential.class).getEntityById(credentialId);
        if (entity == null) {
            throw new NopException(CredentialErrors.ERR_CREDENTIAL_NOT_FOUND)
                    .param(CredentialErrors.ARG_CREDENTIAL_ID, credentialId);
        }

        if (isDeleted(entity)) {
            throw new NopException(CredentialErrors.ERR_CREDENTIAL_DELETED)
                    .param(CredentialErrors.ARG_CREDENTIAL_ID, credentialId);
        }

        // W9 显式增量（设计 §3.5）：oauth2 类型 status=disabled 全路径拒绝
        // （发起/回调/刷新/取用；非 OAuth 类型维持一期语义——仅 delFlag，零变更）
        assertOauth2NotDisabled(entity);

        return entity;
    }

    private boolean isDeleted(NopCredential entity) {
        Byte delFlag = entity.getDelFlag();
        return delFlag != null && delFlag != 0;
    }

    @SuppressWarnings("unchecked")
    private CredentialData decryptToData(NopCredential entity) {
        String cv1Text = entity.getData();
        if (cv1Text == null || cv1Text.isEmpty()) {
            return new CredentialData(new LinkedHashMap<>());
        }

        String json = credentialCipher.decrypt(cv1Text);
        Map<String, Object> fields = JsonTool.parseMap(json);
        if (fields == null) {
            fields = new LinkedHashMap<>();
        }
        return new CredentialData(fields);
    }

    private String maskValue(String value, boolean sensitive) {
        if (value == null) {
            return null;
        }
        if (sensitive) {
            return "****";
        }
        if (value.length() <= 8) {
            return value;
        }
        return value.substring(0, 8) + "...";
    }

    private String resultToString(TestResult result) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("success", result.isSuccess());
        map.put("message", result.getMessage());
        map.put("testedAt", StringHelper.formatDate(result.getTestedAt(), "yyyy-MM-dd HH:mm:ss"));
        return JSON.stringify(map);
    }
}
