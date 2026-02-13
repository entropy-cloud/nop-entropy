
package io.nop.retry.service.entity;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.biz.crud.CrudBizModel;

import io.nop.retry.biz.INopRetryTemplateBiz;
import io.nop.retry.dao.entity.NopRetryTemplate;

@BizModel("NopRetryTemplate")
public class NopRetryTemplateBizModel extends CrudBizModel<NopRetryTemplate> implements INopRetryTemplateBiz{
    public NopRetryTemplateBizModel(){
        setEntityName(NopRetryTemplate.class.getName());
    }
}
