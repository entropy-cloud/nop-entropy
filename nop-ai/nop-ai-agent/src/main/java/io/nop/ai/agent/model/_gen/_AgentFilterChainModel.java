package io.nop.ai.agent.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.ai.agent.model.AgentFilterChainModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/ai/agent.xdef <p>
 * W3-2 (declarative filter chain): ordered filter ID lists that
 * resolve to IAgentMiddleware at assembly time (ResolvedFilterChain
 * pattern). input-filters = request-side guardrails (default PRE_CALL,
 * fires once per request — single-trigger semantics, avoids the
 * N+M+K multi-trigger of PRE_REASONING/PRE_ACTING). output-filters =
 * response-side guardrails (default POST_CALL). Optional points
 * attribute overrides the default lifecycle mapping (D2).
 * filter-definitions provides the agent-internal id->impl mapping
 * (D1 option B — self-contained, no IoC container injection).
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _AgentFilterChainModel extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: filter-definitions
     * 
     */
    private KeyedList<io.nop.ai.agent.model.FilterDefModel> _filterDefinitions = KeyedList.emptyList();
    
    /**
     *  
     * xml name: input-filters
     * 
     */
    private java.util.List<io.nop.ai.agent.model.FilterRefModel> _inputFilters = java.util.Collections.emptyList();
    
    /**
     *  
     * xml name: output-filters
     * 
     */
    private java.util.List<io.nop.ai.agent.model.FilterRefModel> _outputFilters = java.util.Collections.emptyList();
    
    /**
     * 
     * xml name: filter-definitions
     *  
     */
    
    public java.util.List<io.nop.ai.agent.model.FilterDefModel> getFilterDefinitions(){
      return _filterDefinitions;
    }

    
    public void setFilterDefinitions(java.util.List<io.nop.ai.agent.model.FilterDefModel> value){
        checkAllowChange();
        
        this._filterDefinitions = KeyedList.fromList(value, io.nop.ai.agent.model.FilterDefModel::getId);
           
    }

    
    public io.nop.ai.agent.model.FilterDefModel getFilterDef(String name){
        return this._filterDefinitions.getByKey(name);
    }

    public boolean hasFilterDef(String name){
        return this._filterDefinitions.containsKey(name);
    }

    public void addFilterDef(io.nop.ai.agent.model.FilterDefModel item) {
        checkAllowChange();
        java.util.List<io.nop.ai.agent.model.FilterDefModel> list = this.getFilterDefinitions();
        if (list == null || list.isEmpty()) {
            list = new KeyedList<>(io.nop.ai.agent.model.FilterDefModel::getId);
            setFilterDefinitions(list);
        }
        list.add(item);
    }
    
    public java.util.Set<String> keySet_filterDefinitions(){
        return this._filterDefinitions.keySet();
    }

    public boolean hasFilterDefinitions(){
        return !this._filterDefinitions.isEmpty();
    }
    
    /**
     * 
     * xml name: input-filters
     *  
     */
    
    public java.util.List<io.nop.ai.agent.model.FilterRefModel> getInputFilters(){
      return _inputFilters;
    }

    
    public void setInputFilters(java.util.List<io.nop.ai.agent.model.FilterRefModel> value){
        checkAllowChange();
        
        this._inputFilters = value;
           
    }

    
    /**
     * 
     * xml name: output-filters
     *  
     */
    
    public java.util.List<io.nop.ai.agent.model.FilterRefModel> getOutputFilters(){
      return _outputFilters;
    }

    
    public void setOutputFilters(java.util.List<io.nop.ai.agent.model.FilterRefModel> value){
        checkAllowChange();
        
        this._outputFilters = value;
           
    }

    

    @Override
    public void freeze(boolean cascade){
        if(frozen()) return;
        super.freeze(cascade);

        if(cascade){ //NOPMD - suppressed EmptyControlStatement - Auto Gen Code
        
           this._filterDefinitions = io.nop.api.core.util.FreezeHelper.deepFreeze(this._filterDefinitions);
            
           this._inputFilters = io.nop.api.core.util.FreezeHelper.deepFreeze(this._inputFilters);
            
           this._outputFilters = io.nop.api.core.util.FreezeHelper.deepFreeze(this._outputFilters);
            
        }
    }

    @Override
    protected void outputJson(IJsonHandler out){
        super.outputJson(out);
        
        out.putNotNull("filterDefinitions",this.getFilterDefinitions());
        out.putNotNull("inputFilters",this.getInputFilters());
        out.putNotNull("outputFilters",this.getOutputFilters());
    }

    public AgentFilterChainModel cloneInstance(){
        AgentFilterChainModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(AgentFilterChainModel instance){
        super.copyTo(instance);
        
        instance.setFilterDefinitions(this.getFilterDefinitions());
        instance.setInputFilters(this.getInputFilters());
        instance.setOutputFilters(this.getOutputFilters());
    }

    protected AgentFilterChainModel newInstance(){
        return (AgentFilterChainModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
