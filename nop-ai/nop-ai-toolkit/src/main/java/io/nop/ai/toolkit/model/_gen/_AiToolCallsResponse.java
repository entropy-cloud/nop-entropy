package io.nop.ai.toolkit.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.ai.toolkit.model.AiToolCallsResponse;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/ai/tool/call-tools-response.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _AiToolCallsResponse extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: 
     * 
     */
    private KeyedList<io.nop.ai.toolkit.model.AiToolCallResult> _results = KeyedList.emptyList();
    
    /**
     * 
     * xml name: 
     *  
     */
    
    public java.util.List<io.nop.ai.toolkit.model.AiToolCallResult> getResults(){
      return _results;
    }

    
    public void setResults(java.util.List<io.nop.ai.toolkit.model.AiToolCallResult> value){
        checkAllowChange();
        
        this._results = KeyedList.fromList(value, io.nop.ai.toolkit.model.AiToolCallResult::getId);
           
    }

    
    public java.util.Set<String> keySet_results(){
        return this._results.keySet();
    }

    public boolean hasResults(){
        return !this._results.isEmpty();
    }
    

    @Override
    public void freeze(boolean cascade){
        if(frozen()) return;
        super.freeze(cascade);

        if(cascade){ //NOPMD - suppressed EmptyControlStatement - Auto Gen Code
        
           this._results = io.nop.api.core.util.FreezeHelper.deepFreeze(this._results);
            
        }
    }

    @Override
    protected void outputJson(IJsonHandler out){
        super.outputJson(out);
        
        out.putNotNull("results",this.getResults());
    }

    public AiToolCallsResponse cloneInstance(){
        AiToolCallsResponse instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(AiToolCallsResponse instance){
        super.copyTo(instance);
        
        instance.setResults(this.getResults());
    }

    protected AiToolCallsResponse newInstance(){
        return (AiToolCallsResponse) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
