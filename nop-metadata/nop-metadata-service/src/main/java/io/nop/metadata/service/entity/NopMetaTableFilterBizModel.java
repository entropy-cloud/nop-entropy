
package io.nop.metadata.service.entity;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.biz.crud.CrudBizModel;

import io.nop.metadata.biz.INopMetaTableFilterBiz;
import io.nop.metadata.dao.entity.NopMetaTableFilter;

@BizModel("NopMetaTableFilter")
public class NopMetaTableFilterBizModel extends CrudBizModel<NopMetaTableFilter> implements INopMetaTableFilterBiz{
    public NopMetaTableFilterBizModel(){
        setEntityName(NopMetaTableFilter.class.getName());
    }
}
