
package io.nop.metadata.service.entity;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.api.core.annotations.core.Name;
import io.nop.biz.crud.CrudBizModel;
import io.nop.core.context.IServiceContext;
import io.nop.metadata.biz.INopMetaClassificationBiz;
import io.nop.metadata.dao.entity.NopMetaClassification;
import io.nop.metadata.service.search.NopMetaSearchService;
import io.nop.search.api.SearchableDoc;
import jakarta.inject.Inject;

import java.util.Map;
import java.util.Set;

@BizModel("NopMetaClassification")
public class NopMetaClassificationBizModel extends CrudBizModel<NopMetaClassification> implements INopMetaClassificationBiz {

    @Inject
    protected NopMetaSearchService searchService;

    public NopMetaClassificationBizModel() {
        setEntityName(NopMetaClassification.class.getName());
    }

    @Override
    public NopMetaClassification save(@Name("data") Map<String, Object> data, IServiceContext context) {
        NopMetaClassification saved = super.save(data, context);
        searchService.addToIndex("Classification", saved.getClassificationId(), toSearchableDoc(saved));
        return saved;
    }

    @Override
    public boolean delete(@Name("id") String id, IServiceContext context) {
        NopMetaClassification before = dao().getEntityById(id);
        boolean deleted = super.delete(id, context);
        if (before != null) {
            searchService.removeFromIndex("Classification", id);
        }
        return deleted;
    }

    private SearchableDoc toSearchableDoc(NopMetaClassification entity) {
        SearchableDoc doc = new SearchableDoc();
        doc.setId(entity.getClassificationId());
        doc.setName(entity.getName());
        doc.setTitle(entity.getDisplayName());
        doc.setSummary(truncate(entity.getDescription(), 500));
        doc.setContent(join(" ", entity.getName(), entity.getDisplayName(), entity.getDescription()));
        doc.setTagSet(Set.of("Classification"));
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
