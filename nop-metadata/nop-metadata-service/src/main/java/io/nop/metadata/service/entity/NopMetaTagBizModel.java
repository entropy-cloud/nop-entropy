
package io.nop.metadata.service.entity;

import java.util.Map;
import java.util.Set;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.api.core.annotations.core.Name;
import io.nop.biz.crud.CrudBizModel;
import io.nop.commons.util.CollectionHelper;
import io.nop.core.context.IServiceContext;
import io.nop.metadata.biz.INopMetaClassificationBiz;
import io.nop.metadata.biz.INopMetaTagBiz;
import io.nop.metadata.dao.entity.NopMetaClassification;
import io.nop.metadata.dao.entity.NopMetaTag;
import io.nop.metadata.service.NopMetadataHelper;
import io.nop.metadata.service.search.NopMetaSearchProcessor;
import io.nop.search.api.SearchableDoc;
import jakarta.inject.Inject;

@BizModel("NopMetaTag")
public class NopMetaTagBizModel extends CrudBizModel<NopMetaTag> implements INopMetaTagBiz {

    @Inject
    protected NopMetaSearchProcessor searchService;

    /** 跨聚合访问（plan 353 MD-1）：Classification 经 Biz 接口而非 dao 直连。 */
    @Inject
    protected INopMetaClassificationBiz classificationBiz;

    public NopMetaTagBizModel() {
        setEntityName(NopMetaTag.class.getName());
    }

    @Override
    public NopMetaTag save(@Name("data") Map<String, Object> data, IServiceContext context) {
        // P2-19（plan 2026-08-16-0226-3）：null/empty data 提前委托基类（形态统一），统一抛
        // ERR_BIZ_EMPTY_DATA_FOR_SAVE；此后 data 非空，条件内不再需要 null 判
        if (CollectionHelper.isEmptyMap(data)) {
            return super.save(data, context);
        }
        if (data.get(NopMetaTag.PROP_NAME_fullyQualifiedName) == null) {
            String tagName = (String) data.get(NopMetaTag.PROP_NAME_name);
            String classificationId = (String) data.get(NopMetaTag.PROP_NAME_classificationId);
            String parentTagId = (String) data.get(NopMetaTag.PROP_NAME_parentTagId);

            if (tagName != null && classificationId != null) {
                if (parentTagId == null) {
                    NopMetaClassification cls = classificationBiz.get(classificationId, false, context);
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
        NopMetaTag before = requireEntity(id, "delete", context);
        boolean deleted = super.delete(id, context);
        searchService.removeFromIndex("Tag", id);
        return deleted;
    }

    private SearchableDoc toSearchableDoc(NopMetaTag entity) {
        SearchableDoc doc = new SearchableDoc();
        doc.setId(entity.getTagId());
        doc.setName(entity.getName());
        doc.setTitle(entity.getDisplayName());
        doc.setSummary(NopMetadataHelper.truncate(entity.getDescription(), 500));
        doc.setContent(NopMetadataHelper.join(" ", entity.getName(), entity.getFullyQualifiedName(), entity.getDisplayName(), entity.getDescription()));
        doc.setTagSet(Set.of("Tag"));
        return doc;
    }
}
