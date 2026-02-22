
package io.nop.code.service.entity;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.biz.crud.CrudBizModel;

import io.nop.code.biz.INopCodeAnnotationUsageBiz;
import io.nop.code.dao.entity.NopCodeAnnotationUsage;

@BizModel("NopCodeAnnotationUsage")
public class NopCodeAnnotationUsageBizModel extends CrudBizModel<NopCodeAnnotationUsage> implements INopCodeAnnotationUsageBiz{
    public NopCodeAnnotationUsageBizModel(){
        setEntityName(NopCodeAnnotationUsage.class.getName());
    }
}
