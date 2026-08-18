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
import io.nop.credential.api.CredentialLookup;
import io.nop.credential.api.ICredentialMigrationSupport;
import io.nop.credential.dao.entity.NopCredential;
import io.nop.credential.dao.entity.NopCredentialUsage;
import io.nop.credential.service.entity.NopCredentialBizModel;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * {@link ICredentialMigrationSupport} 的实现（W16-impl SPI 增量裁定 2）。
 *
 * <p>创建凭证复用 {@link NopCredentialBizModel#saveCredential} 语义（类型校验 + 加密 +
 * scope=system + 管理员/内部调用分级 + 审计载体 = 凭证行本身），本类不复制加密与归属逻辑。
 *
 * <p>反查语义：
 * <ul>
 *   <li>按名反查跳过软删墓碑行（名称无唯一约束，多条活跃命中按 credentialId 升序取首条——确定性）；</li>
 *   <li>按 consumerRef 反查命中软删凭证时同样返回（{@code deleted=true}）——迁移调用方将其计入
 *       失败清单供人工处置，不跳过不重建（避免同 consumerRef 双凭证歧义）。</li>
 * </ul>
 */
public class CredentialMigrationSupportImpl implements ICredentialMigrationSupport {

    @Inject
    protected IDaoProvider daoProvider;

    /**
     * 创建路径委托的凭证管理 BizModel（saveCredential 语义单点；字段为 protected 兼容 NopIoC 字段注入）。
     */
    @Inject
    protected NopCredentialBizModel credentialBizModel;

    public void setDaoProvider(IDaoProvider daoProvider) {
        this.daoProvider = daoProvider;
    }

    public void setCredentialBizModel(NopCredentialBizModel credentialBizModel) {
        this.credentialBizModel = credentialBizModel;
    }

    @Override
    public CredentialLookup findCredentialByName(String typeName, String name) {
        IEntityDao<NopCredential> dao = daoProvider.daoFor(NopCredential.class);
        QueryBean query = new QueryBean();
        query.addFilter(FilterBeans.eq(NopCredential.PROP_NAME_typeName, typeName));
        query.addFilter(FilterBeans.eq(NopCredential.PROP_NAME_name, name));
        // 跳过软删墓碑行（名称辅助反查不承载身份，命中墓碑不构成失败——身份由 consumerRef 承载）
        query.addFilter(FilterBeans.eq(NopCredential.PROP_NAME_delFlag, 0));
        query.addOrderField(NopCredential.PROP_NAME_credentialId, true);
        List<NopCredential> hits = dao.findAllByQuery(query);
        if (hits.isEmpty()) {
            return null;
        }
        NopCredential first = hits.get(0);
        return new CredentialLookup(first.getCredentialId(), first.getTypeName(), false);
    }

    @Override
    public CredentialLookup findCredentialIdByConsumerRef(String consumerRef) {
        IEntityDao<NopCredentialUsage> usageDao = daoProvider.daoFor(NopCredentialUsage.class);
        QueryBean usageQuery = new QueryBean();
        usageQuery.addFilter(FilterBeans.eq(NopCredentialUsage.PROP_NAME_consumerRef, consumerRef));
        List<NopCredentialUsage> usages = usageDao.findAllByQuery(usageQuery);
        if (usages.isEmpty()) {
            return null;
        }

        // 同一 consumerRef 正常至多一条 usage 行（bind/unbind 维护）；多行（手工 DB 修改等异常态）
        // 时确定性收敛：优先活跃凭证，其次按 credentialId 升序取首条
        IEntityDao<NopCredential> dao = daoProvider.daoFor(NopCredential.class);
        List<NopCredential> credentials = new ArrayList<>();
        for (NopCredentialUsage usage : usages) {
            NopCredential credential = dao.getEntityById(usage.getCredentialId());
            if (credential != null) {
                credentials.add(credential);
            }
        }
        if (credentials.isEmpty()) {
            return null;
        }

        credentials.sort(Comparator.comparing(NopCredential::getCredentialId));
        NopCredential active = null;
        for (NopCredential credential : credentials) {
            if (!isDeleted(credential)) {
                active = credential;
                break;
            }
        }
        NopCredential chosen = active != null ? active : credentials.get(0);
        return new CredentialLookup(chosen.getCredentialId(), chosen.getTypeName(), isDeleted(chosen));
    }

    @Override
    public String createCredential(String typeName, String name, Map<String, Object> fields) {
        // scope 显式 system（迁移工具语义：渠道/数据源凭证一律部署级平台资产）；context 参数在
        // 创建路径未被 saveCredential 使用，传 null
        NopCredential entity = credentialBizModel.saveCredential(typeName, name, fields, null,
                CredentialOwnership.SCOPE_SYSTEM, null, null);
        return entity.getCredentialId();
    }

    private static boolean isDeleted(NopCredential entity) {
        Byte delFlag = entity.getDelFlag();
        return delFlag != null && delFlag != 0;
    }
}
