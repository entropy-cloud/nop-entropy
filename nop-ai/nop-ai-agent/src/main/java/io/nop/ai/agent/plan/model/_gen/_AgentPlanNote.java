package io.nop.ai.agent.plan.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.ai.agent.plan.model.AgentPlanNote;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/ai/agent-plan.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _AgentPlanNote extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: 
     * 
     */
    private java.lang.String _body ;
    
    /**
     *  
     * xml name: id
     * 
     */
    private java.lang.String _id ;
    
    /**
     *  
     * xml name: relatedTaskNo
     * 
     */
    private java.lang.String _relatedTaskNo ;
    
    /**
     * 
     * xml name: 
     *  
     */
    
    public java.lang.String getBody(){
      return _body;
    }

    
    public void setBody(java.lang.String value){
        checkAllowChange();
        
        this._body = value;
           
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
        
        out.putNotNull("body",this.getBody());
        out.putNotNull("id",this.getId());
        out.putNotNull("relatedTaskNo",this.getRelatedTaskNo());
    }

    public AgentPlanNote cloneInstance(){
        AgentPlanNote instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(AgentPlanNote instance){
        super.copyTo(instance);
        
        instance.setBody(this.getBody());
        instance.setId(this.getId());
        instance.setRelatedTaskNo(this.getRelatedTaskNo());
    }

    protected AgentPlanNote newInstance(){
        return (AgentPlanNote) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
