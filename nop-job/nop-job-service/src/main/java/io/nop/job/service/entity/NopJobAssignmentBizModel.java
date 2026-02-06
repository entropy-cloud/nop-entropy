
package io.nop.job.service.entity;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.biz.crud.CrudBizModel;

import io.nop.job.dao.entity.NopJobAssignment;
import io.nop.job.biz.INopJobAssignmentBiz;

@BizModel("NopJobAssignment")
public class NopJobAssignmentBizModel extends CrudBizModel<NopJobAssignment> implements INopJobAssignmentBiz {
    public NopJobAssignmentBizModel(){
        setEntityName(NopJobAssignment.class.getName());
    }
}
