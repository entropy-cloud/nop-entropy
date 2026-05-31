package io.nop.code.service.entity;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.biz.crud.CrudBizModel;
import io.nop.code.biz.INopCodeFlowMembershipBiz;
import io.nop.code.dao.entity.NopCodeFlowMembership;
@BizModel("NopCodeFlowMembership")
public class NopCodeFlowMembershipBizModel extends CrudBizModel<NopCodeFlowMembership> implements INopCodeFlowMembershipBiz{
    public NopCodeFlowMembershipBizModel(){
        setEntityName(NopCodeFlowMembership.class.getName());
    }
}
