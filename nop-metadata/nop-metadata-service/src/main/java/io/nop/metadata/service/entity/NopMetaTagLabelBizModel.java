
package io.nop.metadata.service.entity;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.api.core.annotations.core.Name;
import io.nop.api.core.beans.FilterBeans;
import io.nop.api.core.beans.query.QueryBean;
import io.nop.biz.crud.CrudBizModel;
import io.nop.core.context.IServiceContext;
import io.nop.api.core.time.CoreMetrics;
import io.nop.core.lang.json.JsonTool;
import io.nop.dao.api.IEntityDao;
import io.nop.metadata.biz.INopMetaTagLabelBiz;
import io.nop.metadata.dao.entity.NopMetaGlossaryTerm;
import io.nop.metadata.dao.entity.NopMetaTagLabel;

import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@BizModel("NopMetaTagLabel")
public class NopMetaTagLabelBizModel extends CrudBizModel<NopMetaTagLabel> implements INopMetaTagLabelBiz {
    public NopMetaTagLabelBizModel() {
        setEntityName(NopMetaTagLabel.class.getName());
    }

    @Override
    public NopMetaTagLabel save(@Name("data") Map<String, Object> data, IServiceContext context) {
        NopMetaTagLabel saved = super.save(data, context);

        if ("Glossary".equals(saved.getSource()) && saved.getGlossaryTermId() != null) {
            propagateFromGlossaryTerm(saved, context);
        }

        return saved;
    }

    private void propagateFromGlossaryTerm(NopMetaTagLabel sourceLabel, IServiceContext context) {
        IEntityDao<NopMetaGlossaryTerm> termDao = daoFor(NopMetaGlossaryTerm.class);
        IEntityDao<NopMetaTagLabel> tagLabelDao = daoFor(NopMetaTagLabel.class);

        NopMetaGlossaryTerm term = termDao.getEntityById(sourceLabel.getGlossaryTermId());
        if (term == null || term.getTags() == null || term.getTags().isEmpty()) {
            return;
        }

        List<Object> tagIds = (List<Object>) JsonTool.parse(term.getTags());
        if (tagIds == null || tagIds.isEmpty()) {
            return;
        }

        Timestamp now = CoreMetrics.currentTimestamp();
        String userId = context.getUserId() != null ? context.getUserId() : "system";

        for (Object tagIdObj : tagIds) {
            String tagId = tagIdObj.toString();

            if (existingPropagatedLabel(tagLabelDao, sourceLabel, tagId)) {
                continue;
            }

            NopMetaTagLabel derived = tagLabelDao.newEntity();
            derived.setTagLabelId(UUID.randomUUID().toString().replace("-", ""));
            derived.setSource("Classification");
            derived.setTagId(tagId);
            derived.setLabelType("Derived");
            derived.setState("Suggested");
            derived.setEntityType(sourceLabel.getEntityType());
            derived.setEntityId(sourceLabel.getEntityId());
            derived.setReason("propagated from glossary term " + term.getName());
            derived.setVersion(1L);
            derived.setCreatedBy(userId);
            derived.setCreateTime(now);
            derived.setUpdatedBy(userId);
            derived.setUpdateTime(now);
            tagLabelDao.saveEntity(derived);
        }
    }

    private boolean existingPropagatedLabel(IEntityDao<NopMetaTagLabel> dao, NopMetaTagLabel source, String tagId) {
        QueryBean q = new QueryBean();
        q.addFilter(FilterBeans.eq(NopMetaTagLabel.PROP_NAME_entityType, source.getEntityType()));
        q.addFilter(FilterBeans.eq(NopMetaTagLabel.PROP_NAME_entityId, source.getEntityId()));
        q.addFilter(FilterBeans.eq(NopMetaTagLabel.PROP_NAME_tagId, tagId));
        q.addFilter(FilterBeans.eq(NopMetaTagLabel.PROP_NAME_labelType, "Derived"));
        return dao.findFirstByQuery(q) != null;
    }
}
