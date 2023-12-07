package io.nop.wf.core.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from [153:18:0:0]/nop/schema/wf/wf.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116"})
public abstract class _WfTransitionToAssignedModel extends io.nop.wf.core.model.WfTransitionToModel {
    
    /**
     *  
     * xml name: backLink
     * 
     */
    private java.lang.Boolean _backLink  = false;
    
    /**
     * 
     * xml name: backLink
     *  
     */
    
    public java.lang.Boolean getBackLink(){
      return _backLink;
    }

    
    public void setBackLink(java.lang.Boolean value){
        checkAllowChange();
        
        this._backLink = value;
           
    }

    

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
        
        out.put("backLink",this.getBackLink());
    }
}
 // resume CPD analysis - CPD-ON
