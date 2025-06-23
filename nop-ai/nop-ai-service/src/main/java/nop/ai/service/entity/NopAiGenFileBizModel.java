
package nop.ai.service.entity;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.biz.crud.CrudBizModel;

import nop.ai.dao.entity.NopAiGenFile;

@BizModel("NopAiGenFile")
public class NopAiGenFileBizModel extends CrudBizModel<NopAiGenFile>{
    public NopAiGenFileBizModel(){
        setEntityName(NopAiGenFile.class.getName());
    }
}
