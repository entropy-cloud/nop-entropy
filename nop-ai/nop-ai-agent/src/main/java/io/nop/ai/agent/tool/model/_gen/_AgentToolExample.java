package io.nop.ai.agent.tool.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.ai.agent.tool.model.AgentToolExample;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/ai/tool/tool.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _AgentToolExample extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: call-tools
     * 
     */
    private io.nop.ai.agent.model.AgentCallTools _callTools ;
    
    /**
     *  
     * xml name: call-tools-response
     * 
     */
    private io.nop.ai.agent.tool.model.AgentCallToolsResponse _callToolsResponse ;
    
    /**
     * 
     * xml name: call-tools
     *  
     */
    
    public io.nop.ai.agent.model.AgentCallTools getCallTools(){
      return _callTools;
    }

    
    public void setCallTools(io.nop.ai.agent.model.AgentCallTools value){
        checkAllowChange();
        
        this._callTools = value;
           
    }

    
    /**
     * 
     * xml name: call-tools-response
     *  
     */
    
    public io.nop.ai.agent.tool.model.AgentCallToolsResponse getCallToolsResponse(){
      return _callToolsResponse;
    }

    
    public void setCallToolsResponse(io.nop.ai.agent.tool.model.AgentCallToolsResponse value){
        checkAllowChange();
        
        this._callToolsResponse = value;
           
    }

    

    @Override
    public void freeze(boolean cascade){
        if(frozen()) return;
        super.freeze(cascade);

        if(cascade){ //NOPMD - suppressed EmptyControlStatement - Auto Gen Code
        
           this._callTools = io.nop.api.core.util.FreezeHelper.deepFreeze(this._callTools);
            
           this._callToolsResponse = io.nop.api.core.util.FreezeHelper.deepFreeze(this._callToolsResponse);
            
        }
    }

    @Override
    protected void outputJson(IJsonHandler out){
        super.outputJson(out);
        
        out.putNotNull("callTools",this.getCallTools());
        out.putNotNull("callToolsResponse",this.getCallToolsResponse());
    }

    public AgentToolExample cloneInstance(){
        AgentToolExample instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(AgentToolExample instance){
        super.copyTo(instance);
        
        instance.setCallTools(this.getCallTools());
        instance.setCallToolsResponse(this.getCallToolsResponse());
    }

    protected AgentToolExample newInstance(){
        return (AgentToolExample) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
