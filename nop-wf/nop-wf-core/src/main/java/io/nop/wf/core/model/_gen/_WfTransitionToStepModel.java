package io.nop.wf.core.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.wf.core.model.WfTransitionToStepModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from [159:18:0:0]/nop/schema/wf/wf.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
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
        
        out.putNotNull("backLink",this.isBackLink());
        out.putNotNull("stepName",this.getStepName());
    }

    public WfTransitionToStepModel cloneInstance(){
        WfTransitionToStepModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(WfTransitionToStepModel instance){
        super.copyTo(instance);
        
        instance.setBackLink(this.isBackLink());
        instance.setStepName(this.getStepName());
    }

    protected WfTransitionToStepModel newInstance(){
        return (WfTransitionToStepModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
