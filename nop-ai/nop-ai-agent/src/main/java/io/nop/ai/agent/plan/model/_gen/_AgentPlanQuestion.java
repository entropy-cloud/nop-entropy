package io.nop.ai.agent.plan.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.ai.agent.plan.model.AgentPlanQuestion;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/ai/agent-plan.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _AgentPlanQuestion extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: answerText
     * 
     */
    private java.lang.String _answerText ;
    
    /**
     *  
     * xml name: answeredAt
     * 
     */
    private java.time.LocalDateTime _answeredAt ;
    
    /**
     *  
     * xml name: askedAt
     * 
     */
    private java.time.LocalDateTime _askedAt ;
    
    /**
     *  
     * xml name: id
     * 
     */
    private java.lang.String _id ;
    
    /**
     *  
     * xml name: questionText
     * 
     */
    private java.lang.String _questionText ;
    
    /**
     *  
     * xml name: relatedTaskNo
     * 
     */
    private java.lang.String _relatedTaskNo ;
    
    /**
     * 
     * xml name: answerText
     *  
     */
    
    public java.lang.String getAnswerText(){
      return _answerText;
    }

    
    public void setAnswerText(java.lang.String value){
        checkAllowChange();
        
        this._answerText = value;
           
    }

    
    /**
     * 
     * xml name: answeredAt
     *  
     */
    
    public java.time.LocalDateTime getAnsweredAt(){
      return _answeredAt;
    }

    
    public void setAnsweredAt(java.time.LocalDateTime value){
        checkAllowChange();
        
        this._answeredAt = value;
           
    }

    
    /**
     * 
     * xml name: askedAt
     *  
     */
    
    public java.time.LocalDateTime getAskedAt(){
      return _askedAt;
    }

    
    public void setAskedAt(java.time.LocalDateTime value){
        checkAllowChange();
        
        this._askedAt = value;
           
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
     * xml name: questionText
     *  
     */
    
    public java.lang.String getQuestionText(){
      return _questionText;
    }

    
    public void setQuestionText(java.lang.String value){
        checkAllowChange();
        
        this._questionText = value;
           
    }

    
    /**
     * 
     * xml name: relatedTaskNo
     *  
     */
    
    public java.lang.String getRelatedTaskNo(){
      return _relatedTaskNo;
    }

    
    public void setRelatedTaskNo(java.lang.String value){
        checkAllowChange();
        
        this._relatedTaskNo = value;
           
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
        
        out.putNotNull("answerText",this.getAnswerText());
        out.putNotNull("answeredAt",this.getAnsweredAt());
        out.putNotNull("askedAt",this.getAskedAt());
        out.putNotNull("id",this.getId());
        out.putNotNull("questionText",this.getQuestionText());
        out.putNotNull("relatedTaskNo",this.getRelatedTaskNo());
    }

    public AgentPlanQuestion cloneInstance(){
        AgentPlanQuestion instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(AgentPlanQuestion instance){
        super.copyTo(instance);
        
        instance.setAnswerText(this.getAnswerText());
        instance.setAnsweredAt(this.getAnsweredAt());
        instance.setAskedAt(this.getAskedAt());
        instance.setId(this.getId());
        instance.setQuestionText(this.getQuestionText());
        instance.setRelatedTaskNo(this.getRelatedTaskNo());
    }

    protected AgentPlanQuestion newInstance(){
        return (AgentPlanQuestion) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
