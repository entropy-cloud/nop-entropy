
package io.nop.datav.service.entity;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.biz.crud.CrudBizModel;

import io.nop.datav.biz.INopDatavScreenSnapshotBiz;
import io.nop.datav.dao.entity.NopDatavScreenSnapshot;

@BizModel("NopDatavScreenSnapshot")
public class NopDatavScreenSnapshotBizModel extends CrudBizModel<NopDatavScreenSnapshot> implements INopDatavScreenSnapshotBiz{
    public NopDatavScreenSnapshotBizModel(){
        setEntityName(NopDatavScreenSnapshot.class.getName());
    }
}
