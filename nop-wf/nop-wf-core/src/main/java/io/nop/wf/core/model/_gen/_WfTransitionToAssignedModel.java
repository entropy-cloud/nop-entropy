package io.nop.wf.core.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.wf.core.model.WfTransitionToAssignedModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/wf/wf.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
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
        
        out.putNotNull("backLink",this.getBackLink());
    }

    public WfTransitionToAssignedModel cloneInstance(){
        WfTransitionToAssignedModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(WfTransitionToAssignedModel instance){
        super.copyTo(instance);
        
        instance.setBackLink(this.getBackLink());
    }

    protected WfTransitionToAssignedModel newInstance(){
        return (WfTransitionToAssignedModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
