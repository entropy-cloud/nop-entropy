package io.nop.wf.core.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.wf.core.model.WfTransitionToEmptyModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from [151:18:0:0]/nop/schema/wf/wf.xdef <p>
 * 迁移到空步骤。结束本步骤，但是没有创建新的步骤实例
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _WfTransitionToEmptyModel extends io.nop.wf.core.model.WfTransitionToModel {
    

    @Override
    public void freeze(boolean cascade){
        if(frozen()) return;
        super.freeze(cascade);

        if(cascade){ //NOPMD - suppressed EmptyControlStatement - Auto Gen Code
        
        }
    }

    @Override
    protected void outputJson(IJsonHandler out){
        super.outputJson(out);
        
    }

    public WfTransitionToEmptyModel cloneInstance(){
        WfTransitionToEmptyModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(WfTransitionToEmptyModel instance){
        super.copyTo(instance);
        
    }

    protected WfTransitionToEmptyModel newInstance(){
        return (WfTransitionToEmptyModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
