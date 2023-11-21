/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.wf.dao.entity;

import io.nop.api.core.annotations.biz.BizObjName;
import io.nop.wf.api.actor.IWfActor;
import io.nop.wf.core.store.IWorkflowActionRecord;
import io.nop.wf.dao.entity._gen._NopWfAction;


@BizObjName("NopWfAction")
public class NopWfAction extends _NopWfAction implements IWorkflowActionRecord {
    public NopWfAction(){
    }

    @Override
    public void setCaller(IWfActor caller) {
        if(caller == null){
            setCallerId(null);
            setCallerName(null);
        }else{
            setCallerId(caller.getActorId());
            setCallerName(caller.getActorName());
        }
    }
}
