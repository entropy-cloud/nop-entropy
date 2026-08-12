
package io.nop.ai.service.entity;

import io.nop.ai.biz.INopAiModelBiz;
import io.nop.ai.dao.entity.NopAiModel;
import io.nop.api.core.annotations.biz.BizModel;
import io.nop.core.context.IServiceContext;
import jakarta.annotation.Nullable;
import io.nop.biz.crud.CrudBizModel;
import io.nop.commons.util.StringHelper;
import io.nop.credential.api.ICredentialProvider;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * {@code NopAiModel} 的管理 BizModel（W7-successor 接线：credentialId 引用计数）。
 *
 * <p><b>引用计数接线（plan 2026-08-13-1118-3 Phase 2）</b>：覆盖标准 {@code save}，在持久化后按
 * credentialId 新旧值差异调用 {@link ICredentialProvider#registerUsage} /
 * {@link ICredentialProvider#unregisterUsage}，consumerRef 约定 {@code ai:NopAiModel:<modelId>}
 * （{@code docs-for-ai/03-modules/nop-ai.md` 约定）。语义：
 * <ul>
 *   <li>空 → 非空：registerUsage（bind）</li>
 *   <li>非空 A → 非空 B（A≠B）：unregisterUsage(A) + registerUsage(B)（换绑）</li>
 *   <li>非空 → 空：unregisterUsage（解绑）</li>
 *   <li>不变：noop</li>
 * </ul>
 * registerUsage 幂等（{@code UK_..._CRED_CONSUMER} 唯一约束）。凭证删除时由 nop-credential 的引用计数
 * 拦截（{@code NopCredentialBizModel.delete} 查 {@code NopCredentialUsage} 计数）。
 *
 * <p><b>可选装配</b>：{@link ICredentialProvider} 经 {@code @Nullable} 可选注入——部署不含 nop-credential
 * 时为 null，此时跳过引用计数（save 正常进行）。字段为 protected（NopIoC 字段注入可见性，AGENTS.md）。
 */
@BizModel("NopAiModel")
public class NopAiModelBizModel extends CrudBizModel<NopAiModel> implements INopAiModelBiz {

    private static final Logger LOG = LoggerFactory.getLogger(NopAiModelBizModel.class);

    /**
     * consumerRef 前缀（{@code ai:NopAiModel:<modelId>}，docs-for-ai/03-modules/nop-ai.md 约定）。
     */
    static final String CONSUMER_REF_PREFIX = "ai:NopAiModel:";

    /**
     * 凭证消费 SPI（可选装配）。无 {@code @Inject} 字段注解——仅经 {@link #setCredentialProvider} 的
     * {@code @Nullable} setter 注入（NopIoC optional），避免部署不含 nop-credential 时启动失败。
     */
    protected ICredentialProvider credentialProvider;

    public NopAiModelBizModel() {
        setEntityName(NopAiModel.class.getName());
    }

    /**
     * 可选注入（NopIoC：{@code @Nullable} → optional=true）。部署不含 nop-credential 时注入 null，
     * 此时引用计数接线被跳过（save 正常进行）。
     */
    @Inject
    public void setCredentialProvider(@Nullable ICredentialProvider credentialProvider) {
        this.credentialProvider = credentialProvider;
    }

    /**
     * 覆盖标准 save：先读旧 credentialId，持久化后按新旧差异 reconcile 引用计数。
     *
     * <p>读旧值用 {@code dao().getEntityById}（save 前，事务内）。super.save 完成持久化后取新值。
     * 引用计数操作（register/unregister）与 save 同事务（BizMutation 事务内），保证一致性。
     */
    @Override
    public NopAiModel save(Map<String, Object> data, IServiceContext context) {
        String oldCredentialId = readOldCredentialId(data);
        NopAiModel saved = super.save(data, context);
        reconcileCredentialUsage(saved, oldCredentialId);
        return saved;
    }

    /**
     * 从 save 入参读旧 credentialId（仅更新场景；新建无旧值）。data 无 id 或 id 不存在 → null。
     */
    private String readOldCredentialId(Map<String, Object> data) {
        if (data == null) {
            return null;
        }
        Object idVal = data.get("id");
        if (idVal == null || StringHelper.isEmpty(idVal.toString())) {
            return null; // 新建
        }
        NopAiModel existing = dao().getEntityById(idVal.toString());
        return existing != null ? existing.getCredentialId() : null;
    }

    /**
     * 按新旧 credentialId 差异调用 register/unregister。credentialProvider 为 null 时跳过（部署无凭证库）。
     * consumerRef = {@code ai:NopAiModel:<modelId>}。包可见以便单元测试覆盖 bind/换绑/解绑语义。
     */
    void reconcileCredentialUsage(NopAiModel saved, String oldCredentialId) {
        if (credentialProvider == null) {
            LOG.debug("NopAiModelBizModel: ICredentialProvider not deployed; skipping credential usage reconciliation for model {}", saved.getId());
            return;
        }
        String newCredentialId = saved.getCredentialId();
        String consumerRef = consumerRef(saved.getId());

        if (StringHelper.isEmpty(oldCredentialId) && !StringHelper.isEmpty(newCredentialId)) {
            // bind
            credentialProvider.registerUsage(newCredentialId, consumerRef);
        } else if (!StringHelper.isEmpty(oldCredentialId) && StringHelper.isEmpty(newCredentialId)) {
            // unbind
            credentialProvider.unregisterUsage(oldCredentialId, consumerRef);
        } else if (!StringHelper.isEmpty(oldCredentialId) && !StringHelper.isEmpty(newCredentialId)
                && !oldCredentialId.equals(newCredentialId)) {
            // switch: unregister old + register new
            credentialProvider.unregisterUsage(oldCredentialId, consumerRef);
            credentialProvider.registerUsage(newCredentialId, consumerRef);
        }
        // same or both empty: noop
    }

    static String consumerRef(String modelId) {
        return CONSUMER_REF_PREFIX + modelId;
    }
}
