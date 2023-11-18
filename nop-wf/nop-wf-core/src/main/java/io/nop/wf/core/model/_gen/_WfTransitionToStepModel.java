package io.nop.wf.core.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from [151:18:0:0]/nop/schema/wf/wf.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement"})
public abstract class _WfTransitionToStepModel extends io.nop.wf.core.model.WfTransitionToModel {
    
    /**
     *  
     * xml name: backLink
     * 如果是后向连接，则在将流程节点整理为树型结构时将会忽略此连接。
     */
    private boolean _backLink  = false;
    
    /**
     *  
     * xml name: stepName
     * 
     */
    private java.lang.String _stepName ;
    
    /**
     * 
     * xml name: backLink
     *  如果是后向连接，则在将流程节点整理为树型结构时将会忽略此连接。
     */
    
    public boolean isBackLink(){
      return _backLink;
    }

    
    public void setBackLink(boolean value){
        checkAllowChange();
        
        this._backLink = value;
           
    }

    
    /**
     * 
     * xml name: stepName
     *  
     */
    
    public java.lang.String getStepName(){
      return _stepName;
    }

    
    public void setStepName(java.lang.String value){
        checkAllowChange();
        
        this._stepName = value;
           
    }

    

    public void freeze(boolean cascade){
        if(frozen()) return;
        super.freeze(cascade);

        if(cascade){ //NOPMD - suppressed EmptyControlStatement - Auto Gen Code
        
        }
    }

    protected void outputJson(IJsonHandler out){
        super.outputJson(out);
        
        out.put("backLink",this.isBackLink());
        out.put("stepName",this.getStepName());
    }
}
 // resume CPD analysis - CPD-ON
