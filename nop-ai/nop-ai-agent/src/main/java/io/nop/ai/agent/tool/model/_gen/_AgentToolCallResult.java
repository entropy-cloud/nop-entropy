package io.nop.ai.agent.tool.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.ai.agent.tool.model.AgentToolCallResult;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/ai/tool/call-tools-response.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _AgentToolCallResult extends io.nop.ai.agent.tool.model.ToolCallResult {
    
    /**
     *  
     * xml name: sessionId
     * 
     */
    private java.lang.String _sessionId ;
    
    /**
     *  
     * xml name: 
     * 
     */
    private java.lang.String _type ;
    
    /**
     * 
     * xml name: sessionId
     *  
     */
    
    public java.lang.String getSessionId(){
      return _sessionId;
    }

    
    public void setSessionId(java.lang.String value){
        checkAllowChange();
        
        this._sessionId = value;
           
    }

    
    /**
     * 
     * xml name: 
     *  
     */
    
    public java.lang.String getType(){
      return _type;
    }

    
    public void setType(java.lang.String value){
        checkAllowChange();
        
        this._type = value;
           
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
        
        out.putNotNull("sessionId",this.getSessionId());
        out.putNotNull("type",this.getType());
    }

    public AgentToolCallResult cloneInstance(){
        AgentToolCallResult instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(AgentToolCallResult instance){
        super.copyTo(instance);
        
        instance.setSessionId(this.getSessionId());
        instance.setType(this.getType());
    }

    protected AgentToolCallResult newInstance(){
        return (AgentToolCallResult) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
