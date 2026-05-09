
package io.nop.code.service.entity;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.biz.crud.CrudBizModel;

import io.nop.code.biz.INopCodeDependencyBiz;
import io.nop.code.dao.entity.NopCodeDependency;

@BizModel("NopCodeDependency")
public class NopCodeDependencyBizModel extends CrudBizModel<NopCodeDependency> implements INopCodeDependencyBiz{
    public NopCodeDependencyBizModel(){
        setEntityName(NopCodeDependency.class.getName());
    }
}
