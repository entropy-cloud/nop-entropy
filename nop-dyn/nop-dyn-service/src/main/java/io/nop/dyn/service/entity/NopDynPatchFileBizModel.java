
package io.nop.dyn.service.entity;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.biz.crud.CrudBizModel;

import io.nop.dyn.dao.entity.NopDynPatchFile;

@BizModel("NopDynPatchFile")
public class NopDynPatchFileBizModel extends CrudBizModel<NopDynPatchFile>{
    public NopDynPatchFileBizModel(){
        setEntityName(NopDynPatchFile.class.getName());
    }
}
