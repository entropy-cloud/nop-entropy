package io.nop.wf.core.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.wf.core.model.WfSubFlowModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/wf/wf.xdef <p>
 * 子流程
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _WfSubFlowModel extends io.nop.wf.core.model.WfStepModel {
    
    /**
     *  
     * xml name: start
     * 
     */
    private io.nop.wf.core.model.WfSubFlowStartModel _start ;
    
    /**
     * 
     * xml name: start
     *  
     */
    
    public io.nop.wf.core.model.WfSubFlowStartModel getStart(){
      return _start;
    }

    
    public void setStart(io.nop.wf.core.model.WfSubFlowStartModel value){
        checkAllowChange();
        
        this._start = value;
           
    }

    

    @Override
    public void freeze(boolean cascade){
        if(frozen()) return;
        super.freeze(cascade);

        if(cascade){ //NOPMD - suppressed EmptyControlStatement - Auto Gen Code
        
           this._start = io.nop.api.core.util.FreezeHelper.deepFreeze(this._start);
            
        }
    }

    @Override
    protected void outputJson(IJsonHandler out){
        super.outputJson(out);
        
        out.putNotNull("start",this.getStart());
    }

    public WfSubFlowModel cloneInstance(){
        WfSubFlowModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(WfSubFlowModel instance){
        super.copyTo(instance);
        
        instance.setStart(this.getStart());
    }

    protected WfSubFlowModel newInstance(){
        return (WfSubFlowModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
