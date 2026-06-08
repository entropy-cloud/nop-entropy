
package io.nop.ai.service.entity;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.biz.crud.CrudBizModel;

import io.nop.ai.biz.INopAiTodoBiz;
import io.nop.ai.dao.entity.NopAiTodo;

@BizModel("NopAiTodo")
public class NopAiTodoBizModel extends CrudBizModel<NopAiTodo> implements INopAiTodoBiz{
    public NopAiTodoBizModel(){
        setEntityName(NopAiTodo.class.getName());
    }
}
