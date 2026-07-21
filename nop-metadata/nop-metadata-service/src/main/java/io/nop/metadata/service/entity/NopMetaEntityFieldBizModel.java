package io.nop.metadata.service.entity;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.api.core.annotations.core.Name;
import io.nop.biz.crud.CrudBizModel;
import io.nop.core.context.IServiceContext;
import io.nop.dao.api.IEntityDao;
import io.nop.metadata.biz.INopMetaEntityFieldBiz;
import io.nop.metadata.dao.entity.NopMetaEntity;
import io.nop.metadata.dao.entity.NopMetaEntityField;
import io.nop.metadata.service.search.NopMetaSearchService;
import io.nop.search.api.SearchableDoc;
import jakarta.inject.Inject;

import java.util.Map;
import java.util.Set;

@BizModel("NopMetaEntityField")
public class NopMetaEntityFieldBizModel extends CrudBizModel<NopMetaEntityField> implements INopMetaEntityFieldBiz{
    @Inject
    protected NopMetaSearchService searchService;

    public NopMetaEntityFieldBizModel() {
        setEntityName(NopMetaEntityField.class.getName());
    }

    @Override
    public NopMetaEntityField save(@Name("data") Map<String, Object> data, IServiceContext context) {
        String businessDomainId = stringOf(data, NopMetaEntityField.PROP_NAME_businessDomainId);
        if (businessDomainId == null || businessDomainId.isEmpty()) {
            String metaEntityId = stringOf(data, NopMetaEntityField.PROP_NAME_metaEntityId);
            if (metaEntityId != null && !metaEntityId.isEmpty()) {
                IEntityDao<NopMetaEntity> entityDao = daoFor(NopMetaEntity.class);
                NopMetaEntity parentEntity = entityDao.getEntityById(metaEntityId);
                if (parentEntity != null && parentEntity.getBusinessDomainId() != null) {
                    data.put(NopMetaEntityField.PROP_NAME_businessDomainId, parentEntity.getBusinessDomainId());
                }
            }
        }
        NopMetaEntityField saved = super.save(data, context);
        searchService.addToIndex("MetaEntityField", saved.getEntityFieldId(), toSearchableDoc(saved));
        return saved;
    }

    @Override
    public boolean delete(@Name("id") String id, IServiceContext context) {
        NopMetaEntityField before = dao().getEntityById(id);
        boolean deleted = super.delete(id, context);
        if (before != null) {
            searchService.removeFromIndex("MetaEntityField", id);
        }
        return deleted;
    }

    private SearchableDoc toSearchableDoc(NopMetaEntityField entity) {
        SearchableDoc doc = new SearchableDoc();
        doc.setId(entity.getEntityFieldId());
        doc.setName(entity.getFieldName());
        doc.setTitle(entity.getDisplayName());
        doc.setSummary(truncate(entity.getComment(), 500));
        doc.setContent(join(" ", entity.getFieldName(), entity.getColumnCode(), entity.getDisplayName(), entity.getComment()));
        doc.setTagSet(Set.of("MetaEntityField"));
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

    private static String stringOf(Map<String, Object> data, String key) {
        Object v = data.get(key);
        return v == null ? null : v.toString();
    }
}
