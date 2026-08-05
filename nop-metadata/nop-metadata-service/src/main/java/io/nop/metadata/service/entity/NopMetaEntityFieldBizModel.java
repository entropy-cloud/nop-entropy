package io.nop.metadata.service.entity;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.api.core.annotations.core.Name;
import io.nop.biz.crud.CrudBizModel;
import io.nop.core.context.IServiceContext;
import io.nop.dao.api.IEntityDao;
import io.nop.metadata.biz.INopMetaEntityFieldBiz;
import io.nop.metadata.dao.entity.NopMetaEntity;
import io.nop.metadata.dao.entity.NopMetaEntityField;
import io.nop.metadata.service.NopMetadataHelper;
import io.nop.metadata.service.search.NopMetaSearchProcessor;
import io.nop.search.api.SearchableDoc;
import jakarta.inject.Inject;

import java.util.Map;
import java.util.Set;

@BizModel("NopMetaEntityField")
public class NopMetaEntityFieldBizModel extends CrudBizModel<NopMetaEntityField> implements INopMetaEntityFieldBiz{
    @Inject
    protected NopMetaSearchProcessor searchService;

    public NopMetaEntityFieldBizModel() {
        setEntityName(NopMetaEntityField.class.getName());
    }

    @Override
    public NopMetaEntityField save(@Name("data") Map<String, Object> data, IServiceContext context) {
        String businessDomainId = NopMetadataHelper.stringOf(data, NopMetaEntityField.PROP_NAME_businessDomainId);
        if (businessDomainId == null || businessDomainId.isEmpty()) {
            String metaEntityId = NopMetadataHelper.stringOf(data, NopMetaEntityField.PROP_NAME_metaEntityId);
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
        NopMetaEntityField before = requireEntity(id, "delete", context);
        boolean deleted = super.delete(id, context);
        searchService.removeFromIndex("MetaEntityField", id);
        return deleted;
    }

    private SearchableDoc toSearchableDoc(NopMetaEntityField entity) {
        return NopMetadataHelper.toSearchableDoc(entity);
    }
}
