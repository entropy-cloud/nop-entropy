
package io.nop.metadata.service.entity;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.api.core.annotations.biz.BizMutation;
import io.nop.api.core.annotations.core.Name;
import io.nop.api.core.annotations.core.Optional;
import io.nop.api.core.beans.FilterBeans;
import io.nop.api.core.beans.query.QueryBean;
import io.nop.metadata.service.NopMetadataErrors;
import io.nop.metadata.service.NopMetadataException;
import io.nop.metadata.service.NopMetadataArgs;
import io.nop.metadata.service.NopMetadataHelper;

import io.nop.api.core.time.CoreMetrics;
import io.nop.biz.crud.CrudBizModel;
import io.nop.commons.util.CollectionHelper;
import io.nop.core.context.IServiceContext;
import io.nop.core.lang.json.JsonTool;
import io.nop.dao.api.IEntityDao;
import io.nop.metadata.biz.INopMetaTagLabelBiz;
import io.nop.metadata.dao.entity.NopMetaGlossaryTerm;
import io.nop.metadata.dao.entity.NopMetaTagLabel;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static io.nop.metadata.service.NopMetadataErrors.ERR_TAG_LABEL_INVALID_LABEL_TYPE;
import static io.nop.metadata.service.NopMetadataErrors.ERR_TAG_LABEL_SUBMIT_APPROVAL_FAILED;
import static io.nop.metadata.service.NopMetadataErrors.ARG_ERROR;
import static io.nop.metadata.service.NopMetadataErrors.ARG_LABEL_TYPE;
import static io.nop.metadata.service.NopMetadataErrors.ARG_TAG_LABEL_ID;

@BizModel("NopMetaTagLabel")
public class NopMetaTagLabelBizModel extends CrudBizModel<NopMetaTagLabel> implements INopMetaTagLabelBiz {

    private static final Logger LOG = LoggerFactory.getLogger(NopMetaTagLabelBizModel.class);

    @Inject
    protected LineageTagPropagationProcessor lineageTagPropagationProcessor;

    @Inject
    protected AutoClassificationProcessor autoClassificationProcessor;

    public NopMetaTagLabelBizModel() {
        setEntityName(NopMetaTagLabel.class.getName());
    }

    @BizMutation
    public List<NopMetaTagLabel> propagateTags(@Name("entityType") String entityType,
                                                @Name("entityId") String entityId,
                                                @Optional @Name("tagId") String tagId,
                                                IServiceContext context) {
        return lineageTagPropagationProcessor.propagateTags(entityType, entityId, tagId, context);
    }

    @BizMutation
    public List<NopMetaTagLabel> suggestTags(@Name("entityType") String entityType,
                                              @Name("entityId") String entityId,
                                              IServiceContext context) {
        return autoClassificationProcessor.suggestTags(entityType, entityId, context);
    }

    @Override
    public NopMetaTagLabel save(@Name("data") Map<String, Object> data, IServiceContext context) {
        // P2-19（plan 2026-08-16-0226-3）：null/empty data 提前委托基类，
        // 统一抛 ERR_BIZ_EMPTY_DATA_FOR_SAVE
        // （不 NPE 抢先——下方 containsKey 会先解引用 data）
        if (CollectionHelper.isEmptyMap(data)) {
            return super.save(data, context);
        }
        Map<String, Object> effectiveData = data;
        if (!data.containsKey("state")) {
            effectiveData = new java.util.HashMap<>(data);
            String labelType = (String) data.get("labelType");
            if ("Manual".equals(labelType)) {
                effectiveData.put("state", "Confirmed");
            } else if (labelType != null) {
                effectiveData.put("state", "Suggested");
            }
        }

        NopMetaTagLabel saved = super.save(effectiveData, context);

        // P2-01（plan 2026-08-16-0920-1，裁决选项 ii）：GLOSSARY 来源、tagId=NULL 的行不被
        // UK_NOP_META_TAG_LABEL 约束（复合 UK 任一列 NULL 即豁免唯一性，NULL-distinct），
        // 显式客户端 save/update 可累积重复行。守卫在 super.save 之后对最终实体状态查重
        // （部分字段 update 形态经基类合并后才可见完整列值），命中 fail-loud 抛出，
        // mutation 事务整体回滚（与 P2-09 trySubmitForApproval 失败回滚同语义）。
        rejectDuplicateGlossaryTermLabel(saved);

        if ("Glossary".equals(saved.getSource()) && saved.getGlossaryTermId() != null) {
            propagateFromGlossaryTerm(saved, context);
        }

        triggerApprovalIfNeeded(saved, context);

        return saved;
    }

    @Override
    public NopMetaTagLabel update(@Name("data") Map<String, Object> data, IServiceContext context) {
        NopMetaTagLabel updated = super.update(data, context);
        // P2-01：update 写入面对称防护（改 entityId/glossaryTermId 撞向既有 GLOSSARY null-tag 行
        // 与重复 save 同族），与 save 共用同一守卫与查重键。
        rejectDuplicateGlossaryTermLabel(updated);
        return updated;
    }

    /**
     * P2-01 查重守卫：仅覆盖 DB UK 的 NULL-distinct 豁免面（source=Glossary 且 tagId=NULL
     * 且 glossaryTermId 非 NULL）。查重键与 UK_NOP_META_TAG_LABEL 对齐扩展：
     * (entityType, entityId, source, glossaryTermId, tagId IS NULL)，save/update 带自身
     * id 时排除自身（幂等自更新不误伤）。tagId 非 NULL 的行（含 GLOSSARY source）继续由
     * DB UK 数据库级拒绝，守卫不接管。沿 existingPropagatedLabel（Derived 预检）与
     * ERR_SQL_VIEW_TABLE_EXISTS（find-or-fail）先例。
     */
    private void rejectDuplicateGlossaryTermLabel(NopMetaTagLabel label) {
        if (label == null || !"Glossary".equals(label.getSource())
                || label.getTagId() != null || label.getGlossaryTermId() == null) {
            return;
        }
        QueryBean q = new QueryBean();
        q.addFilter(FilterBeans.eq(NopMetaTagLabel.PROP_NAME_entityType, label.getEntityType()));
        q.addFilter(FilterBeans.eq(NopMetaTagLabel.PROP_NAME_entityId, label.getEntityId()));
        q.addFilter(FilterBeans.eq(NopMetaTagLabel.PROP_NAME_source, label.getSource()));
        q.addFilter(FilterBeans.eq(NopMetaTagLabel.PROP_NAME_glossaryTermId, label.getGlossaryTermId()));
        q.addFilter(FilterBeans.isNull(NopMetaTagLabel.PROP_NAME_tagId));
        boolean hasOther = dao().findAllByQuery(q).stream()
                .anyMatch(row -> !row.getTagLabelId().equals(label.getTagLabelId()));
        if (hasOther) {
            throw new NopMetadataException(NopMetadataErrors.ERR_TAG_LABEL_DUPLICATE_GLOSSARY_TERM)
                    .param(NopMetadataArgs.ARG_ENTITY_TYPE, label.getEntityType())
                    .param(NopMetadataArgs.ARG_ENTITY_ID, label.getEntityId())
                    .param(NopMetadataArgs.ARG_GLOSSARY_TERM_ID, label.getGlossaryTermId());
        }
    }

    private void triggerApprovalIfNeeded(NopMetaTagLabel entity, IServiceContext context) {
        String wfName = getWfNameFromMeta();
        if (wfName == null) return;

        String labelType = entity.getLabelType();
        if (labelType == null) return;

        if ("Manual".equals(labelType)) {
            entity.setState("Confirmed");
            // 实体刚经 super.save 保存，处于 SAVING 态；updateEntity 仅接受 MANAGED 态（P2-MA5-401
            // 激活本路径后暴露：DAO 级检查抛 update-entity-not-managed），saveOrUpdate 兼容两种状态。
            dao().saveOrUpdateEntity(entity);
        } else if ("Derived".equals(labelType) || "Propagated".equals(labelType) || "Automated".equals(labelType)) {
            entity.setState("Suggested");
            dao().saveOrUpdateEntity(entity);
            trySubmitForApproval(entity, context);
        } else {
            throw new NopMetadataException(ERR_TAG_LABEL_INVALID_LABEL_TYPE)
                    .param(ARG_LABEL_TYPE, labelType);
        }
    }

    private String getWfNameFromMeta() {
        try {
            // wf:wfName 是 xmeta 根节点属性（NopMetaTagLabel.xmeta 根元素 attr），不在 props map 中——
            // SchemaImpl.getProp 只读 props 恒返回 null，导致自动提审静默失效（P2-MA5-401）。
            // 改经 IExtensibleObject.prop_get 读取根属性，与 approval-support.xbiz:30 的
            // objMeta['wf:wfName'] 属性访问同机制（missing 时因名称含 ':' 不抛未知属性，返回 null）。
            Object val = bizObjectManager().getBizObject("NopMetaTagLabel")
                    .getObjMeta().prop_get("wf:wfName");
            return val instanceof String ? (String) val : null;
        } catch (Exception e) {
            // R2.11（P2-MA4-001）：不静默吞异常——记录完整异常（含堆栈），调用方按 wfName==null 降级（不自动提审）
            LOG.warn("nop.metadata.taglabel.wf-name-read-failed, errorCode={}",
                    NopMetadataErrors.ERR_ENTITY_SYNC_ISOLATED.getErrorCode(), e);
            return null;
        }
    }

    private void trySubmitForApproval(NopMetaTagLabel entity, IServiceContext context) {
        try {
            bizObjectManager().getBizObject("NopMetaTagLabel")
                    .invoke("submitForApproval", Map.of("id", entity.getTagLabelId()), null, context);
        } catch (Exception e) {
            // P2-09（R6.4）：提审失败不得静默跳过——标签已保存但永不进审批流对用户零感知，
            // 属静默数据丢失。fail-loud 抛 ERR_TAG_LABEL_SUBMIT_APPROVAL_FAILED（保留原始异常链），
            // save 事务整体回滚，用户侧可见错误。
            LOG.warn("submitForApproval failed for TagLabel {}, fail-loud (no silent skip)",
                    entity.getTagLabelId(), e);
            throw new NopMetadataException(ERR_TAG_LABEL_SUBMIT_APPROVAL_FAILED, e)
                    .param(ARG_TAG_LABEL_ID, entity.getTagLabelId())
                    .param(ARG_ERROR, NopMetadataHelper.toErrorMessage(e));
        }
    }

    private void propagateFromGlossaryTerm(NopMetaTagLabel sourceLabel, IServiceContext context) {
        IEntityDao<NopMetaGlossaryTerm> termDao = daoFor(NopMetaGlossaryTerm.class);
        NopMetaGlossaryTerm term = termDao.getEntityById(sourceLabel.getGlossaryTermId());
        if (term == null || term.getTags() == null || term.getTags().isEmpty()) {
            return;
        }

        List<Object> tagIds = (List<Object>) JsonTool.parse(term.getTags());
        if (tagIds == null || tagIds.isEmpty()) {
            return;
        }

        String userId = context.getUserId() != null ? context.getUserId() : "system";

        for (Object tagIdObj : tagIds) {
            String tagId = tagIdObj.toString();

            if (existingPropagatedLabel(sourceLabel, tagId)) {
                continue;
            }

            Map<String, Object> data = Map.of(
                    "tagLabelId", UUID.randomUUID().toString().replace("-", ""),
                    "source", "Classification",
                    "tagId", tagId,
                    "labelType", "Derived",
                    "state", "Suggested",
                    "entityType", sourceLabel.getEntityType(),
                    "entityId", sourceLabel.getEntityId(),
                    "reason", "propagated from glossary term " + term.getName()
            );

            try {
                bizObjectManager().getBizObject("NopMetaTagLabel")
                        .invoke("save", Map.of("data", data), null, context);
            } catch (Exception e) {
                LOG.warn("Failed to save propagated TagLabel for tagId={}, errorCode={}",
                        tagId, NopMetadataErrors.ERR_ENTITY_SYNC_ISOLATED.getErrorCode(), e);
            }
        }
    }

    private boolean existingPropagatedLabel(NopMetaTagLabel source, String tagId) {
        QueryBean q = new QueryBean();
        q.addFilter(FilterBeans.eq(NopMetaTagLabel.PROP_NAME_entityType, source.getEntityType()));
        q.addFilter(FilterBeans.eq(NopMetaTagLabel.PROP_NAME_entityId, source.getEntityId()));
        q.addFilter(FilterBeans.eq(NopMetaTagLabel.PROP_NAME_tagId, tagId));
        q.addFilter(FilterBeans.eq(NopMetaTagLabel.PROP_NAME_labelType, "Derived"));
        return dao().findFirstByQuery(q) != null;
    }
}
