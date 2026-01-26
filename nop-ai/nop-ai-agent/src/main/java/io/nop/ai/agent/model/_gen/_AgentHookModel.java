package io.nop.ai.agent.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.ai.agent.model.AgentHookModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/ai/agent.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _AgentHookModel extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: 
     * 
     */
    private io.nop.core.lang.eval.IEvalFunction _body ;
    
    /**
     *  
     * xml name: event
     * 
     */
    private java.lang.String _event ;
    
    /**
     *  
     * xml name: id
     * 
     */
    private java.lang.String _id ;
    
    /**
     * 
     * xml name: 
     *  
     */
    
    public io.nop.core.lang.eval.IEvalFunction getBody(){
      return _body;
    }

    
    public void setBody(io.nop.core.lang.eval.IEvalFunction value){
        checkAllowChange();
        
        this._body = value;
           
    }

    
    /**
     * 
     * xml name: event
     *  
     */
    
    public java.lang.String getEvent(){
      return _event;
    }

    
    public void setEvent(java.lang.String value){
        checkAllowChange();
        
        this._event = value;
           
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
        out.putNotNull("event",this.getEvent());
        out.putNotNull("id",this.getId());
    }

    public AgentHookModel cloneInstance(){
        AgentHookModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(AgentHookModel instance){
        super.copyTo(instance);
        
        instance.setBody(this.getBody());
        instance.setEvent(this.getEvent());
        instance.setId(this.getId());
    }

    protected AgentHookModel newInstance(){
        return (AgentHookModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
