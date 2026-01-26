package io.nop.ai.agent.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.ai.agent.model.AgentPlanDecision;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/ai/plan.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _AgentPlanDecision extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: decisionText
     * 决策内容（大文本）
     */
    private java.lang.String _decisionText ;
    
    /**
     *  
     * xml name: id
     * 
     */
    private java.lang.String _id ;
    
    /**
     *  
     * xml name: madeAt
     * 
     */
    private java.time.LocalDateTime _madeAt ;
    
    /**
     *  
     * xml name: rationale
     * 决策理由（大文本）
     */
    private java.lang.String _rationale ;
    
    /**
     * 
     * xml name: decisionText
     *  决策内容（大文本）
     */
    
    public java.lang.String getDecisionText(){
      return _decisionText;
    }

    
    public void setDecisionText(java.lang.String value){
        checkAllowChange();
        
        this._decisionText = value;
           
    }

    
    /**
     * 
     * xml name: id
     *  
     */
    
    public java.lang.String getId(){
      return _id;
    }

    
    public void setId(java.lang.String value){
        checkAllowChange();
        
        this._id = value;
           
    }

    
    /**
     * 
     * xml name: madeAt
     *  
     */
    
    public java.time.LocalDateTime getMadeAt(){
      return _madeAt;
    }

    
    public void setMadeAt(java.time.LocalDateTime value){
        checkAllowChange();
        
        this._madeAt = value;
           
    }

    
    /**
     * 
     * xml name: rationale
     *  决策理由（大文本）
     */
    
    public java.lang.String getRationale(){
      return _rationale;
    }

    
    public void setRationale(java.lang.String value){
        checkAllowChange();
        
        this._rationale = value;
           
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
        
        out.putNotNull("decisionText",this.getDecisionText());
        out.putNotNull("id",this.getId());
        out.putNotNull("madeAt",this.getMadeAt());
        out.putNotNull("rationale",this.getRationale());
    }

    public AgentPlanDecision cloneInstance(){
        AgentPlanDecision instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(AgentPlanDecision instance){
        super.copyTo(instance);
        
        instance.setDecisionText(this.getDecisionText());
        instance.setId(this.getId());
        instance.setMadeAt(this.getMadeAt());
        instance.setRationale(this.getRationale());
    }

    protected AgentPlanDecision newInstance(){
        return (AgentPlanDecision) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
