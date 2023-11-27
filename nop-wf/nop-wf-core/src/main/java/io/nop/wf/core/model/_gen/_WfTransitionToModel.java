package io.nop.wf.core.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from [142:18:0:0]/nop/schema/wf/wf.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement"})
public abstract class _WfTransitionToModel extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: after-transition
     * 
     */
    private io.nop.core.lang.eval.IEvalAction _afterTransition ;
    
    /**
     *  
     * xml name: before-transition
     * 
     */
    private io.nop.core.lang.eval.IEvalAction _beforeTransition ;
    
    /**
     *  
     * xml name: caseValue
     * 
     */
    private java.lang.String _caseValue ;
    
    /**
     *  
     * xml name: order
     * transition-to节点的执行顺序。当splitType=or的时候，如果排在前面的节点如果满足条件，就不会检查排在后面的节点
     */
    private int _order  = 0;
    
    /**
     *  
     * xml name: when
     * 
     */
    private io.nop.core.lang.eval.IEvalPredicate _when ;
    
    /**
     * 
     * xml name: after-transition
     *  
     */
    
    public io.nop.core.lang.eval.IEvalAction getAfterTransition(){
      return _afterTransition;
    }

    
    public void setAfterTransition(io.nop.core.lang.eval.IEvalAction value){
        checkAllowChange();
        
        this._afterTransition = value;
           
    }

    
    /**
     * 
     * xml name: before-transition
     *  
     */
    
    public io.nop.core.lang.eval.IEvalAction getBeforeTransition(){
      return _beforeTransition;
    }

    
    public void setBeforeTransition(io.nop.core.lang.eval.IEvalAction value){
        checkAllowChange();
        
        this._beforeTransition = value;
           
    }

    
    /**
     * 
     * xml name: caseValue
     *  
     */
    
    public java.lang.String getCaseValue(){
      return _caseValue;
    }

    
    public void setCaseValue(java.lang.String value){
        checkAllowChange();
        
        this._caseValue = value;
           
    }

    
    /**
     * 
     * xml name: order
     *  transition-to节点的执行顺序。当splitType=or的时候，如果排在前面的节点如果满足条件，就不会检查排在后面的节点
     */
    
    public int getOrder(){
      return _order;
    }

    
    public void setOrder(int value){
        checkAllowChange();
        
        this._order = value;
           
    }

    
    /**
     * 
     * xml name: when
     *  
     */
    
    public io.nop.core.lang.eval.IEvalPredicate getWhen(){
      return _when;
    }

    
    public void setWhen(io.nop.core.lang.eval.IEvalPredicate value){
        checkAllowChange();
        
        this._when = value;
           
    }

    

    public void freeze(boolean cascade){
        if(frozen()) return;
        super.freeze(cascade);

        if(cascade){ //NOPMD - suppressed EmptyControlStatement - Auto Gen Code
        
        }
    }

    protected void outputJson(IJsonHandler out){
        super.outputJson(out);
        
        out.put("afterTransition",this.getAfterTransition());
        out.put("beforeTransition",this.getBeforeTransition());
        out.put("caseValue",this.getCaseValue());
        out.put("order",this.getOrder());
        out.put("when",this.getWhen());
    }
}
 // resume CPD analysis - CPD-ON
