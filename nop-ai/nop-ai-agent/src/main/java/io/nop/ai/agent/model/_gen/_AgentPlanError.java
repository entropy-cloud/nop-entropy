package io.nop.ai.agent.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.ai.agent.model.AgentPlanError;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/ai/plan.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _AgentPlanError extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: attemptNumber
     * 
     */
    private java.lang.Integer _attemptNumber ;
    
    /**
     *  
     * xml name: encounteredAt
     * 
     */
    private java.time.LocalDateTime _encounteredAt ;
    
    /**
     *  
     * xml name: errorText
     * 错误描述（大文本）
     */
    private java.lang.String _errorText ;
    
    /**
     *  
     * xml name: id
     * 
     */
    private java.lang.String _id ;
    
    /**
     *  
     * xml name: resolution
     * 错误解决方案（大文本）
     */
    private java.lang.String _resolution ;
    
    /**
     *  
     * xml name: resolvedAt
     * 
     */
    private java.time.LocalDateTime _resolvedAt ;
    
    /**
     * 
     * xml name: attemptNumber
     *  
     */
    
    public java.lang.Integer getAttemptNumber(){
      return _attemptNumber;
    }

    
    public void setAttemptNumber(java.lang.Integer value){
        checkAllowChange();
        
        this._attemptNumber = value;
           
    }

    
    /**
     * 
     * xml name: encounteredAt
     *  
     */
    
    public java.time.LocalDateTime getEncounteredAt(){
      return _encounteredAt;
    }

    
    public void setEncounteredAt(java.time.LocalDateTime value){
        checkAllowChange();
        
        this._encounteredAt = value;
           
    }

    
    /**
     * 
     * xml name: errorText
     *  错误描述（大文本）
     */
    
    public java.lang.String getErrorText(){
      return _errorText;
    }

    
    public void setErrorText(java.lang.String value){
        checkAllowChange();
        
        this._errorText = value;
           
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
     * xml name: resolution
     *  错误解决方案（大文本）
     */
    
    public java.lang.String getResolution(){
      return _resolution;
    }

    
    public void setResolution(java.lang.String value){
        checkAllowChange();
        
        this._resolution = value;
           
    }

    
    /**
     * 
     * xml name: resolvedAt
     *  
     */
    
    public java.time.LocalDateTime getResolvedAt(){
      return _resolvedAt;
    }

    
    public void setResolvedAt(java.time.LocalDateTime value){
        checkAllowChange();
        
        this._resolvedAt = value;
           
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
        
        out.putNotNull("attemptNumber",this.getAttemptNumber());
        out.putNotNull("encounteredAt",this.getEncounteredAt());
        out.putNotNull("errorText",this.getErrorText());
        out.putNotNull("id",this.getId());
        out.putNotNull("resolution",this.getResolution());
        out.putNotNull("resolvedAt",this.getResolvedAt());
    }

    public AgentPlanError cloneInstance(){
        AgentPlanError instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(AgentPlanError instance){
        super.copyTo(instance);
        
        instance.setAttemptNumber(this.getAttemptNumber());
        instance.setEncounteredAt(this.getEncounteredAt());
        instance.setErrorText(this.getErrorText());
        instance.setId(this.getId());
        instance.setResolution(this.getResolution());
        instance.setResolvedAt(this.getResolvedAt());
    }

    protected AgentPlanError newInstance(){
        return (AgentPlanError) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
