
package io.nop.metadata.service.entity;

import java.util.Map;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.api.core.annotations.core.Name;
import io.nop.biz.crud.CrudBizModel;
import io.nop.core.context.IServiceContext;
import io.nop.metadata.biz.INopMetaTagBiz;
import io.nop.metadata.dao.entity.NopMetaClassification;
import io.nop.metadata.dao.entity.NopMetaTag;

@BizModel("NopMetaTag")
public class NopMetaTagBizModel extends CrudBizModel<NopMetaTag> implements INopMetaTagBiz {

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
        return super.save(data, context);
    }
}
