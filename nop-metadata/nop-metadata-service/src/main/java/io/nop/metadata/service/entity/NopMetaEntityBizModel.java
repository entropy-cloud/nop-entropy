
package io.nop.metadata.service.entity;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.api.core.annotations.core.Name;
import io.nop.biz.crud.CrudBizModel;
import io.nop.core.context.IServiceContext;
import io.nop.metadata.biz.INopMetaEntityBiz;
import io.nop.metadata.dao.entity.NopMetaEntity;
import io.nop.metadata.service.NopMetadataHelper;
import io.nop.metadata.service.search.NopMetaSearchService;
import io.nop.search.api.SearchableDoc;
import jakarta.inject.Inject;

import java.util.Map;

@BizModel("NopMetaEntity")
public class NopMetaEntityBizModel extends CrudBizModel<NopMetaEntity> implements INopMetaEntityBiz {

    @Inject
    protected NopMetaSearchService searchService;

    public NopMetaEntityBizModel() {
        setEntityName(NopMetaEntity.class.getName());
    }

    @Override
    public NopMetaEntity save(@Name("data") Map<String, Object> data, IServiceContext context) {
        NopMetaEntity saved = super.save(data, context);
        searchService.addToIndex("MetaEntity", saved.getMetaEntityId(), toSearchableDoc(saved));
        return saved;
    }

    @Override
    public boolean delete(@Name("id") String id, IServiceContext context) {
        NopMetaEntity before = dao().getEntityById(id);
        boolean deleted = super.delete(id, context);
        if (before != null) {
            searchService.removeFromIndex("MetaEntity", id);
        }
        return deleted;
    }

    private SearchableDoc toSearchableDoc(NopMetaEntity entity) {
        return NopMetadataHelper.toSearchableDoc(entity);
    }
}