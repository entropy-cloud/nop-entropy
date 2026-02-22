
package io.nop.code.service.entity;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.biz.crud.CrudBizModel;

import io.nop.code.biz.INopCodeFileBiz;
import io.nop.code.dao.entity.NopCodeFile;

@BizModel("NopCodeFile")
public class NopCodeFileBizModel extends CrudBizModel<NopCodeFile> implements INopCodeFileBiz{
    public NopCodeFileBizModel(){
        setEntityName(NopCodeFile.class.getName());
    }
}
