
package io.nop.metadata.service.entity;

import java.util.Map;
import java.util.Set;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.api.core.annotations.core.Name;
import io.nop.biz.crud.CrudBizModel;
import io.nop.core.context.IServiceContext;
import io.nop.metadata.biz.INopMetaTagBiz;
import io.nop.metadata.dao.entity.NopMetaClassification;
import io.nop.metadata.dao.entity.NopMetaTag;
import io.nop.metadata.service.search.NopMetaSearchService;
import io.nop.search.api.SearchableDoc;
import jakarta.inject.Inject;

@BizModel("NopMetaTag")
public class NopMetaTagBizModel extends CrudBizModel<NopMetaTag> implements INopMetaTagBiz {

    @Inject
    protected NopMetaSearchService searchService;

    public NopMetaTagBizModel() {
        setEntityName(NopMetaTag.class.getName());
    }

    @Override
    public NopMetaTag save(@Name("data") Map<String, Object> data, IServiceContext context) {
        if (data != null && data.get(NopMetaTag.PROP_NAME_fullyQualifiedName) == null) {
            String tagName = (String) data.get(NopMetaTag.PROP_NAME_name);
            String classificationId = (String) data.get(NopMetaTag.PROP_NAME_classificationId);
            String parentTagId = (String) data.get(NopMetaTag.PROP_NAME_parentTagId);

            if (tagName != null && classificationId != null) {
                if (parentTagId == null) {
                    NopMetaClassification cls = daoFor(NopMetaClassification.class).getEntityById(classificationId);
                    if (cls != null && cls.getName() != null) {
                        data.put(NopMetaTag.PROP_NAME_fullyQualifiedName, cls.getName() + "." + tagName);
                    }
                } else {
                    NopMetaTag parentTag = daoFor(NopMetaTag.class).getEntityById(parentTagId);
                    if (parentTag != null && parentTag.getFullyQualifiedName() != null) {
                        data.put(NopMetaTag.PROP_NAME_fullyQualifiedName, parentTag.getFullyQualifiedName() + "." + tagName);
                    }
                }
            }
        }
        NopMetaTag saved = super.save(data, context);
        searchService.addToIndex("Tag", saved.getTagId(), toSearchableDoc(saved));
        return saved;
    }

    @Override
    public boolean delete(@Name("id") String id, IServiceContext context) {
        NopMetaTag before = dao().getEntityById(id);
        boolean deleted = super.delete(id, context);
        if (before != null) {
            searchService.removeFromIndex("Tag", id);
        }
        return deleted;
    }

    private SearchableDoc toSearchableDoc(NopMetaTag entity) {
        SearchableDoc doc = new SearchableDoc();
        doc.setId(entity.getTagId());
        doc.setName(entity.getName());
        doc.setTitle(entity.getDisplayName());
        doc.setSummary(truncate(entity.getDescription(), 500));
        doc.setContent(join(" ", entity.getName(), entity.getFullyQualifiedName(), entity.getDisplayName(), entity.getDescription()));
        doc.setTagSet(Set.of("Tag"));
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
}
