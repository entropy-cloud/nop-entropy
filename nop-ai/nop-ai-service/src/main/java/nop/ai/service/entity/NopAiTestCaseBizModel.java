
package nop.ai.service.entity;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.biz.crud.CrudBizModel;

import nop.ai.dao.entity.NopAiTestCase;

@BizModel("NopAiTestCase")
public class NopAiTestCaseBizModel extends CrudBizModel<NopAiTestCase>{
    public NopAiTestCaseBizModel(){
        setEntityName(NopAiTestCase.class.getName());
    }
}
