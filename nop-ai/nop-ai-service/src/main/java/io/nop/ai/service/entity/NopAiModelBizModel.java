
package io.nop.ai.service.entity;

import io.nop.ai.biz.INopAiModelBiz;
import io.nop.ai.dao.entity.NopAiModel;
import io.nop.api.core.annotations.biz.BizModel;
import io.nop.api.core.annotations.biz.BizMutation;
import io.nop.api.core.annotations.core.Description;
import io.nop.api.core.annotations.core.Name;
import io.nop.api.core.beans.query.QueryBean;
import io.nop.core.context.IServiceContext;
import jakarta.annotation.Nullable;
import io.nop.biz.crud.CrudBizModel;
import io.nop.commons.util.StringHelper;
import io.nop.credential.api.ICredentialProvider;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
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
     * <p>读旧值用 {@code findFirstByExample（审计 AI-17）}（save 前，事务内）。super.save 完成持久化后取新值。
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
        // 审计 AI-17：save 前读旧值改强类型 byExample（等值匹配编译期检查字段名）
        NopAiModel example = dao().newEntity();
        example.setId(idVal.toString());
        NopAiModel existing = dao().findFirstByExample(example);
        return existing != null ? existing.getCredentialId() : null;
    }

    /**
     * 按新旧 credentialId 差异调用 register/unregister。credentialProvider 为 null 时跳过（部署无凭证库）。
     * consumerRef = {@code ai:NopAiModel:<modelId>}。包可见以便单元测试覆盖 bind/换绑/解绑语义。
     *
     * <p>D6-04（A1-audit successor，2026-08-17）：判空统一 isBlank——纯空白 credentialId
     * 视同未配置（走回退/不登记引用），不当有效凭证引用使用。
     */
    void reconcileCredentialUsage(NopAiModel saved, String oldCredentialId) {
        if (credentialProvider == null) {
            LOG.debug("NopAiModelBizModel: ICredentialProvider not deployed; skipping credential usage reconciliation for model {}", saved.getId());
            return;
        }
        String newCredentialId = saved.getCredentialId();
        String consumerRef = consumerRef(saved.getId());

        if (StringHelper.isBlank(oldCredentialId) && !StringHelper.isBlank(newCredentialId)) {
            // bind
            credentialProvider.registerUsage(newCredentialId, consumerRef);
        } else if (!StringHelper.isBlank(oldCredentialId) && StringHelper.isBlank(newCredentialId)) {
            // unbind
            credentialProvider.unregisterUsage(oldCredentialId, consumerRef);
        } else if (!StringHelper.isBlank(oldCredentialId) && !StringHelper.isBlank(newCredentialId)
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

    // ==================== D6-02：删除动作注销引用计数（A1-audit successor，2026-08-17） ====================

    /**
     * 覆盖标准 {@code delete}：删除前读取行的 credentialId，删除成功后注销引用计数。
     *
     * <p><b>动机（D6-02 运维死锁闭合）</b>：模型删除后 usage 行残留 → 凭证删除被
     * {@code NopCredentialBizModel.delete} 的引用计数拦截永久拒绝。删除模型与注销引用
     * 同处一个 BizMutation 事务。provider 未部署（null）或 credentialId 空白时跳过
     * （可选装配语义保持）；{@code unregisterUsage} 按 (credentialId, consumerRef)
     * 删行、天然幂等。
     */
    @Description("@i18n:biz.delete|根据主键删除指定对象")
    @BizMutation
    @Override
    public boolean delete(@Name("id") String id, IServiceContext context) {
        // delete 前捕获 credentialId 供注销引用（审计 AI-17 裁定保留：删除路径无 prepareDelete
        // 旧值回调可用的强类型等价，注释升级为裁定记录）
        NopAiModel existing = dao().getEntityById(id);
        boolean deleted = super.delete(id, context);
        if (deleted && existing != null) {
            unregisterUsageQuietly(existing.getCredentialId(), existing.getId());
        }
        return deleted;
    }

    /**
     * 覆盖 {@code deleteByQuery}：基类路径经 {@code doDeleteByQuery → doDeleteMulti → doDelete}
     * <b>不经过</b>本类覆盖的 {@code delete}（虚分派不发生），故先收集命中行的
     * (id, credentialId)，删除后逐行注销。
     *
     * <p>{@code batchDelete} 无需覆盖——基类 {@code batchDelete} 逐 id 委托
     * {@code delete(entity.orm_idString(), context)}，虚分派到本类覆盖的 {@code delete}，
     * 接线天然成立（Web 管理页 batch-delete 按钮即经该路径）。
     */
    @Description("根据查询条件获取一批实体数据，然后删除这些实体")
    @BizMutation
    @Override
    public int deleteByQuery(@Name("query") QueryBean query, IServiceContext context) {
        List<NopAiModel> hits = findList(query, null, context);
        int deleted = super.deleteByQuery(query, context);
        for (NopAiModel hit : hits) {
            unregisterUsageQuietly(hit.getCredentialId(), hit.getId());
        }
        return deleted;
    }

    /**
     * 注销单行引用（包私有以便测试覆盖）：provider 未部署或 credentialId 空白时静默跳过
     * （无凭证可注销，非缺陷路径）。
     */
    void unregisterUsageQuietly(String credentialId, String modelId) {
        if (credentialProvider == null || StringHelper.isBlank(credentialId)) {
            return;
        }
        credentialProvider.unregisterUsage(credentialId, consumerRef(modelId));
    }
}
