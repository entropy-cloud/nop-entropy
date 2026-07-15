
package io.nop.metadata.service.entity;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.biz.crud.CrudBizModel;

import io.nop.metadata.biz.INopMetaQualityRuleBiz;
import io.nop.metadata.dao.entity.NopMetaQualityRule;

@BizModel("NopMetaQualityRule")
public class NopMetaQualityRuleBizModel extends CrudBizModel<NopMetaQualityRule> implements INopMetaQualityRuleBiz{
    public NopMetaQualityRuleBizModel(){
        setEntityName(NopMetaQualityRule.class.getName());
    }
}
