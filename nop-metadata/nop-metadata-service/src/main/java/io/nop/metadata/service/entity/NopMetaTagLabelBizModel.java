
package io.nop.metadata.service.entity;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.api.core.annotations.core.Name;
import io.nop.api.core.beans.FilterBeans;
import io.nop.api.core.beans.query.QueryBean;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.time.CoreMetrics;
import io.nop.biz.crud.CrudBizModel;
import io.nop.core.context.IServiceContext;
import io.nop.core.lang.json.JsonTool;
import io.nop.dao.api.IEntityDao;
import io.nop.metadata.biz.INopMetaTagLabelBiz;
import io.nop.metadata.dao.entity.NopMetaGlossaryTerm;
import io.nop.metadata.dao.entity.NopMetaTagLabel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static io.nop.metadata.service.NopMetadataErrors.ERR_TAG_LABEL_INVALID_LABEL_TYPE;
import static io.nop.metadata.service.NopMetadataErrors.ARG_LABEL_TYPE;

@BizModel("NopMetaTagLabel")
public class NopMetaTagLabelBizModel extends CrudBizModel<NopMetaTagLabel> implements INopMetaTagLabelBiz {

    private static final Logger LOG = LoggerFactory.getLogger(NopMetaTagLabelBizModel.class);

    public NopMetaTagLabelBizModel() {
        setEntityName(NopMetaTagLabel.class.getName());
    }

    @Override
    public NopMetaTagLabel save(@Name("data") Map<String, Object> data, IServiceContext context) {
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

        if ("Glossary".equals(saved.getSource()) && saved.getGlossaryTermId() != null) {
            propagateFromGlossaryTerm(saved, context);
        }

        triggerApprovalIfNeeded(saved, context);

        return saved;
    }

    private void triggerApprovalIfNeeded(NopMetaTagLabel entity, IServiceContext context) {
        String wfName = getWfNameFromMeta();
        if (wfName == null) return;

        String labelType = entity.getLabelType();
        if (labelType == null) return;

        if ("Manual".equals(labelType)) {
            entity.setState("Confirmed");
            dao().updateEntity(entity);
        } else if ("Derived".equals(labelType) || "Propagated".equals(labelType) || "Automated".equals(labelType)) {
            entity.setState("Suggested");
            dao().updateEntity(entity);
            trySubmitForApproval(entity, context);
        } else {
            throw new NopException(ERR_TAG_LABEL_INVALID_LABEL_TYPE)
                    .param(ARG_LABEL_TYPE, labelType);
        }
    }

    private String getWfNameFromMeta() {
        try {
            Object val = bizObjectManager().getBizObject("NopMetaTagLabel")
                    .getObjMeta().getProp("wf:wfName");
            return val instanceof String ? (String) val : null;
        } catch (Exception e) {
            return null;
        }
    }

    private void trySubmitForApproval(NopMetaTagLabel entity, IServiceContext context) {
        try {
            bizObjectManager().getBizObject("NopMetaTagLabel")
                    .invoke("submitForApproval", Map.of("id", entity.getTagLabelId()), null, context);
        } catch (Exception e) {
            LOG.warn("submitForApproval failed for TagLabel {} (workflow may not be available)",
                    entity.getTagLabelId(), e);
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
                LOG.warn("Failed to save propagated TagLabel for tagId={}", tagId, e);
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
