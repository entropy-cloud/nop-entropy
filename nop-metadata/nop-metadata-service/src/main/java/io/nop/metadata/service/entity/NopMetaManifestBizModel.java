
package io.nop.metadata.service.entity;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.biz.crud.CrudBizModel;

import io.nop.metadata.biz.INopMetaManifestBiz;
import io.nop.metadata.dao.entity.NopMetaManifest;

@BizModel("NopMetaManifest")
public class NopMetaManifestBizModel extends CrudBizModel<NopMetaManifest> implements INopMetaManifestBiz{
    public NopMetaManifestBizModel(){
        setEntityName(NopMetaManifest.class.getName());
    }
}
