
package io.nop.metadata.service.entity;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.api.core.annotations.core.Name;
import io.nop.api.core.beans.FilterBeans;
import io.nop.api.core.beans.query.QueryBean;
import io.nop.api.core.time.CoreMetrics;
import io.nop.biz.crud.CrudBizModel;
import io.nop.core.context.IServiceContext;
import io.nop.core.lang.json.JsonTool;
import io.nop.dao.api.IEntityDao;
import io.nop.metadata.biz.INopMetaGlossaryTermBiz;
import io.nop.metadata.dao.entity.NopMetaGlossaryTerm;
import io.nop.metadata.dao.entity.NopMetaTagLabel;
import io.nop.metadata.service.search.NopMetaSearchService;
import io.nop.search.api.SearchableDoc;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Timestamp;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@BizModel("NopMetaGlossaryTerm")
public class NopMetaGlossaryTermBizModel extends CrudBizModel<NopMetaGlossaryTerm> implements INopMetaGlossaryTermBiz {

    private static final Logger LOG = LoggerFactory.getLogger(NopMetaGlossaryTermBizModel.class);

    public NopMetaGlossaryTermBizModel() {
        setEntityName(NopMetaGlossaryTerm.class.getName());
    }

    @Inject
    protected NopMetaSearchService searchService;

    @Override
    public NopMetaGlossaryTerm save(@Name("data") Map<String, Object> data, IServiceContext context) {
        NopMetaGlossaryTerm saved = super.save(data, context);
        syncTagLabels(saved, context);
        searchService.addToIndex("GlossaryTerm", saved.getGlossaryTermId(), toSearchableDoc(saved));
        return saved;
    }

    @Override
    @io.nop.api.core.annotations.biz.BizMutation
    @io.nop.api.core.annotations.graphql.GraphQLReturn(bizObjName = "NopMetaGlossaryTerm")
    public NopMetaGlossaryTerm update(@Name("data") Map<String, Object> data, IServiceContext context) {
        NopMetaGlossaryTerm updated = super.update(data, context);
        syncTagLabels(updated, context);
        searchService.addToIndex("GlossaryTerm", updated.getGlossaryTermId(), toSearchableDoc(updated));
        return updated;
    }

    @Override
    public boolean delete(@Name("id") String id, IServiceContext context) {
        NopMetaGlossaryTerm before = dao().getEntityById(id);
        boolean deleted = super.delete(id, context);
        if (before != null) {
            searchService.removeFromIndex("GlossaryTerm", id);
        }
        return deleted;
    }

    private SearchableDoc toSearchableDoc(NopMetaGlossaryTerm entity) {
        SearchableDoc doc = new SearchableDoc();
        doc.setId(entity.getGlossaryTermId());
        doc.setName(entity.getName());
        doc.setTitle(entity.getDisplayName());
        doc.setSummary(truncate(entity.getDescription(), 500));
        doc.setContent(join(" ", entity.getName(), entity.getFullyQualifiedName(), entity.getDisplayName(), entity.getDescription(), entity.getSynonyms()));
        doc.setTagSet(Set.of("GlossaryTerm"));
        return doc;
    }

    private static String truncate(String s, int maxLen) {
        if (s == null) return "";
        return s.length() <= maxLen ? s : s.substring(0, maxLen);
    }

    private static String join(String delimiter, String... parts) {
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (part != null && !part.isEmpty()) {
                if (sb.length() > 0) sb.append(delimiter);
                sb.append(part);
            }
        }
        return sb.toString();
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

        String userId = context.getUserId() != null ? context.getUserId() : "system";

        for (String tagId : newTagIds) {
            if (!existingTagIds.contains(tagId)) {
                Map<String, Object> data = Map.of(
                        "tagLabelId", UUID.randomUUID().toString().replace("-", ""),
                        "source", "Classification",
                        "tagId", tagId,
                        "labelType", "Derived",
                        "state", "Suggested",
                        "entityType", "NopMetaGlossaryTerm",
                        "entityId", glossaryTermId,
                        "appliedBy", userId,
                        "appliedAt", CoreMetrics.currentTimestamp()
                );

                try {
                    bizObjectManager().getBizObject("NopMetaTagLabel")
                            .invoke("save", Map.of("data", data), null, context);
                } catch (Exception e) {
                    LOG.warn("Failed to save TagLabel for glossaryTerm {} tagId={}",
                            glossaryTermId, tagId, e);
                }
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
