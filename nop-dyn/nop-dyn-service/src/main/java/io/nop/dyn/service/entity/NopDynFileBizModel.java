
package io.nop.dyn.service.entity;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.biz.crud.CrudBizModel;

import io.nop.dyn.dao.entity.NopDynFile;

@BizModel("NopDynFile")
public class NopDynFileBizModel extends CrudBizModel<NopDynFile>{
    public NopDynFileBizModel(){
        setEntityName(NopDynFile.class.getName());
    }
}
