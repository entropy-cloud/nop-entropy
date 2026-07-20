
package io.nop.metadata.service.entity;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.biz.crud.CrudBizModel;

import io.nop.metadata.biz.INopMetaTagLabelBiz;
import io.nop.metadata.dao.entity.NopMetaTagLabel;

@BizModel("NopMetaTagLabel")
public class NopMetaTagLabelBizModel extends CrudBizModel<NopMetaTagLabel> implements INopMetaTagLabelBiz{
    public NopMetaTagLabelBizModel(){
        setEntityName(NopMetaTagLabel.class.getName());
    }
}
