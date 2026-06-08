
package io.nop.ai.service.entity;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.biz.crud.CrudBizModel;
import io.nop.ai.biz.INopAiTestResultBiz;

import io.nop.ai.dao.entity.NopAiTestResult;

@BizModel("NopAiTestResult")
public class NopAiTestResultBizModel extends CrudBizModel<NopAiTestResult> implements INopAiTestResultBiz {
    public NopAiTestResultBizModel(){
        setEntityName(NopAiTestResult.class.getName());
    }
}
