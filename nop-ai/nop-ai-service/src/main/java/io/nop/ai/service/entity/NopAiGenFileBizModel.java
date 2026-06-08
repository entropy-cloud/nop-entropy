
package io.nop.ai.service.entity;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.biz.crud.CrudBizModel;
import io.nop.ai.biz.INopAiGenFileBiz;

import io.nop.ai.dao.entity.NopAiGenFile;

@BizModel("NopAiGenFile")
public class NopAiGenFileBizModel extends CrudBizModel<NopAiGenFile> implements INopAiGenFileBiz {
    public NopAiGenFileBizModel(){
        setEntityName(NopAiGenFile.class.getName());
    }
}
