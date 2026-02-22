
package io.nop.code.service.entity;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.biz.crud.CrudBizModel;

import io.nop.code.biz.INopCodeSymbolBiz;
import io.nop.code.dao.entity.NopCodeSymbol;

@BizModel("NopCodeSymbol")
public class NopCodeSymbolBizModel extends CrudBizModel<NopCodeSymbol> implements INopCodeSymbolBiz{
    public NopCodeSymbolBizModel(){
        setEntityName(NopCodeSymbol.class.getName());
    }
}
