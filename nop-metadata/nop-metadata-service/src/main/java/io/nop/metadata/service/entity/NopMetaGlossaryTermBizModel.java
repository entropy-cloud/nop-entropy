
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
import io.nop.metadata.biz.INopMetaGlossaryTermBiz;
import io.nop.metadata.dao.entity.NopMetaGlossaryTerm;
import io.nop.metadata.dao.entity.NopMetaTagLabel;

import java.sql.Timestamp;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@BizModel("NopMetaGlossaryTerm")
public class NopMetaGlossaryTermBizModel extends CrudBizModel<NopMetaGlossaryTerm> implements INopMetaGlossaryTermBiz {
    public NopMetaGlossaryTermBizModel() {
        setEntityName(NopMetaGlossaryTerm.class.getName());
    }

    @Override
    public NopMetaGlossaryTerm save(@Name("data") Map<String, Object> data, IServiceContext context) {
        NopMetaGlossaryTerm saved = super.save(data, context);
        syncTagLabels(saved, context);
        return saved;
    }

    @Override
    @io.nop.api.core.annotations.biz.BizMutation
    @io.nop.api.core.annotations.graphql.GraphQLReturn(bizObjName = "NopMetaGlossaryTerm")
    public NopMetaGlossaryTerm update(@Name("data") Map<String, Object> data, IServiceContext context) {
        NopMetaGlossaryTerm updated = super.update(data, context);
        syncTagLabels(updated, context);
        return updated;
    }

    private void syncTagLabels(NopMetaGlossaryTerm term, IServiceContext context) {
        String glossaryTermId = term.getGlossaryTermId();
        String tagsJson = term.getTags();

        IEntityDao<NopMetaTagLabel> tagLabelDao = daoFor(NopMetaTagLabel.class);

        Set<String> newTagIds = parseTagIds(tagsJson);

        List<NopMetaTagLabel> existing = findExistingTagLabels(tagLabelDao, glossaryTermId);
        Set<String> existingTagIds = new HashSet<>();
        for (NopMetaTagLabel label : existing) {
            existingTagIds.add(label.getTagId());
        }

        Timestamp now = CoreMetrics.currentTimestamp();
        String userId = context.getUserId() != null ? context.getUserId() : "system";

        for (String tagId : newTagIds) {
            if (!existingTagIds.contains(tagId)) {
                NopMetaTagLabel label = tagLabelDao.newEntity();
                label.setTagLabelId(UUID.randomUUID().toString().replace("-", ""));
                label.setSource("Classification");
                label.setTagId(tagId);
                label.setLabelType("Derived");
                label.setState("Suggested");
                label.setEntityType("NopMetaGlossaryTerm");
                label.setEntityId(glossaryTermId);
                label.setAppliedBy(userId);
                label.setAppliedAt(now);
                label.setVersion(1L);
                label.setCreatedBy(userId);
                label.setCreateTime(now);
                label.setUpdatedBy(userId);
                label.setUpdateTime(now);
                tagLabelDao.saveEntity(label);
            }
        }

        for (NopMetaTagLabel label : existing) {
            if (!newTagIds.contains(label.getTagId())) {
                tagLabelDao.deleteEntity(label);
            }
        }
    }

    private Set<String> parseTagIds(String tagsJson) {
        Set<String> tagIds = new HashSet<>();
        if (tagsJson != null && !tagsJson.isEmpty()) {
            List<Object> parsed = (List<Object>) JsonTool.parse(tagsJson);
            for (Object obj : parsed) {
                tagIds.add(obj.toString());
            }
        }
        return tagIds;
    }

    private List<NopMetaTagLabel> findExistingTagLabels(IEntityDao<NopMetaTagLabel> dao, String glossaryTermId) {
        QueryBean q = new QueryBean();
        q.addFilter(FilterBeans.eq(NopMetaTagLabel.PROP_NAME_entityType, "NopMetaGlossaryTerm"));
        q.addFilter(FilterBeans.eq(NopMetaTagLabel.PROP_NAME_entityId, glossaryTermId));
        q.addFilter(FilterBeans.eq(NopMetaTagLabel.PROP_NAME_labelType, "Derived"));
        return dao.findAllByQuery(q);
    }
}
