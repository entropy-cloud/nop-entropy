package io.nop.wf.core.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.wf.core.model.WfTransitionToEndModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from [148:18:0:0]/nop/schema/wf/wf.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _WfTransitionToEndModel extends io.nop.wf.core.model.WfTransitionToModel {
    

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

    public WfTransitionToEndModel cloneInstance(){
        WfTransitionToEndModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(WfTransitionToEndModel instance){
        super.copyTo(instance);
        
    }

    protected WfTransitionToEndModel newInstance(){
        return (WfTransitionToEndModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
