
package io.nop.sys.service.entity;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.biz.crud.CrudBizModel;

import io.nop.sys.dao.entity.NopSysClusterLeader;

@BizModel("NopSysClusterLeader")
public class NopSysClusterLeaderBizModel extends CrudBizModel<NopSysClusterLeader>{
    public NopSysClusterLeaderBizModel(){
        setEntityName(NopSysClusterLeader.class.getName());
    }
}
