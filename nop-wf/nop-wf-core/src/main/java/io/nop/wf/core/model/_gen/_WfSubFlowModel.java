package io.nop.wf.core.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from [262:10:0:0]/nop/schema/wf/wf.xdef <p>
 * 子流程
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116"})
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
        
        out.put("start",this.getStart());
    }
}
 // resume CPD analysis - CPD-ON
