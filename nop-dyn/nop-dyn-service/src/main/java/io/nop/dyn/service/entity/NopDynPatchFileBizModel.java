
package io.nop.dyn.service.entity;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.biz.crud.CrudBizModel;

import io.nop.dyn.dao.entity.NopDynPatchFile;
import io.nop.dyn.biz.INopDynPatchFileBiz;

@BizModel("NopDynPatchFile")
public class NopDynPatchFileBizModel extends CrudBizModel<NopDynPatchFile> implements INopDynPatchFileBiz {
    public NopDynPatchFileBizModel(){
        setEntityName(NopDynPatchFile.class.getName());
    }
}
