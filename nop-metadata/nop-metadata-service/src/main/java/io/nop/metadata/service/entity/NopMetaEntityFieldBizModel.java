package io.nop.metadata.service.entity;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.api.core.annotations.core.Name;
import io.nop.biz.crud.CrudBizModel;
import io.nop.core.context.IServiceContext;
import io.nop.dao.api.IEntityDao;
import io.nop.metadata.biz.INopMetaEntityFieldBiz;
import io.nop.metadata.dao.entity.NopMetaEntity;
import io.nop.metadata.dao.entity.NopMetaEntityField;

import java.util.Map;

@BizModel("NopMetaEntityField")
public class NopMetaEntityFieldBizModel extends CrudBizModel<NopMetaEntityField> implements INopMetaEntityFieldBiz{
    public NopMetaEntityFieldBizModel(){
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
        return super.save(data, context);
    }

    private static String stringOf(Map<String, Object> data, String key) {
        Object v = data.get(key);
        return v == null ? null : v.toString();
    }
}
