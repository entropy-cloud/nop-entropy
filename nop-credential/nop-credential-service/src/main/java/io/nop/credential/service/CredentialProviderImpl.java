/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.credential.service;

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
import io.nop.credential.crypto.CredentialCipher;
import io.nop.credential.crypto.CredentialErrors;
import io.nop.credential.dao.entity.NopCredential;
import io.nop.credential.dao.entity.NopCredentialUsage;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import jakarta.inject.Inject;

import java.sql.Timestamp;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
 */
public class CredentialProviderImpl implements ICredentialProvider {

    @Inject
    protected IDaoProvider daoProvider;

    @Inject
    protected CredentialCipher credentialCipher;

    @Inject
    protected ICredentialTypeRegistry credentialTypeRegistry;

    public void setDaoProvider(IDaoProvider daoProvider) {
        this.daoProvider = daoProvider;
    }

    public void setCredentialCipher(CredentialCipher credentialCipher) {
        this.credentialCipher = credentialCipher;
    }

    public void setCredentialTypeRegistry(ICredentialTypeRegistry credentialTypeRegistry) {
        this.credentialTypeRegistry = credentialTypeRegistry;
    }

    @Override
    public CredentialData getCredential(String credentialId) {
        NopCredential entity = loadActiveCredential(credentialId);
        return decryptToData(entity);
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
