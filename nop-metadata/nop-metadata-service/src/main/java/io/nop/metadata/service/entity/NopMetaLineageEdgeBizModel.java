
package io.nop.metadata.service.entity;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.biz.crud.CrudBizModel;

import io.nop.metadata.biz.INopMetaLineageEdgeBiz;
import io.nop.metadata.dao.entity.NopMetaLineageEdge;

@BizModel("NopMetaLineageEdge")
public class NopMetaLineageEdgeBizModel extends CrudBizModel<NopMetaLineageEdge> implements INopMetaLineageEdgeBiz{
    public NopMetaLineageEdgeBizModel(){
        setEntityName(NopMetaLineageEdge.class.getName());
    }
}
