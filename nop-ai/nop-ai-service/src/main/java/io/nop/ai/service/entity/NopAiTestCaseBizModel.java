
package io.nop.ai.service.entity;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.biz.crud.CrudBizModel;
import io.nop.ai.biz.INopAiTestCaseBiz;

import io.nop.ai.dao.entity.NopAiTestCase;

@BizModel("NopAiTestCase")
public class NopAiTestCaseBizModel extends CrudBizModel<NopAiTestCase> implements INopAiTestCaseBiz {
    public NopAiTestCaseBizModel(){
        setEntityName(NopAiTestCase.class.getName());
    }
}
