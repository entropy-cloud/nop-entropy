package io.nop.ai.toolkit.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.ai.toolkit.model.AiToolExample;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/ai/tool/tool.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _AiToolExample extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: call-tools
     * 
     */
    private io.nop.ai.toolkit.model.AiToolCalls _callTools ;
    
    /**
     *  
     * xml name: call-tools-response
     * 
     */
    private io.nop.ai.toolkit.model.AiToolCallsResponse _callToolsResponse ;
    
    /**
     *  
     * xml name: index
     * 
     */
    private int _index ;
    
    /**
     * 
     * xml name: call-tools
     *  
     */
    
    public io.nop.ai.toolkit.model.AiToolCalls getCallTools(){
      return _callTools;
    }

    
    public void setCallTools(io.nop.ai.toolkit.model.AiToolCalls value){
        checkAllowChange();
        
        this._callTools = value;
           
    }

    
    /**
     * 
     * xml name: call-tools-response
     *  
     */
    
    public io.nop.ai.toolkit.model.AiToolCallsResponse getCallToolsResponse(){
      return _callToolsResponse;
    }

    
    public void setCallToolsResponse(io.nop.ai.toolkit.model.AiToolCallsResponse value){
        checkAllowChange();
        
        this._callToolsResponse = value;
           
    }

    
    /**
     * 
     * xml name: index
     *  
     */
    
    public int getIndex(){
      return _index;
    }

    
    public void setIndex(int value){
        checkAllowChange();
        
        this._index = value;
           
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
        out.putNotNull("index",this.getIndex());
    }

    public AiToolExample cloneInstance(){
        AiToolExample instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(AiToolExample instance){
        super.copyTo(instance);
        
        instance.setCallTools(this.getCallTools());
        instance.setCallToolsResponse(this.getCallToolsResponse());
        instance.setIndex(this.getIndex());
    }

    protected AiToolExample newInstance(){
        return (AiToolExample) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
