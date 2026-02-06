
package io.nop.dyn.service.entity;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.biz.crud.CrudBizModel;

import io.nop.dyn.dao.entity.NopDynSql;
import io.nop.dyn.biz.INopDynSqlBiz;

@BizModel("NopDynSql")
public class NopDynSqlBizModel extends CrudBizModel<NopDynSql> implements INopDynSqlBiz {
    public NopDynSqlBizModel(){
        setEntityName(NopDynSql.class.getName());
    }
}
