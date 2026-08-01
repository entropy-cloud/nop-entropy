package io.nop.ai.agent.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.ai.agent.model.AgentRecipeRefModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/ai/agent.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _AgentRecipeRefModel extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: params
     * 
     */
    private KeyedList<io.nop.ai.agent.model.RecipeParamModel> _params = KeyedList.emptyList();
    
    /**
     *  
     * xml name: ref
     * 
     */
    private java.lang.String _ref ;
    
    /**
     * 
     * xml name: params
     *  
     */
    
    public java.util.List<io.nop.ai.agent.model.RecipeParamModel> getParams(){
      return _params;
    }

    
    public void setParams(java.util.List<io.nop.ai.agent.model.RecipeParamModel> value){
        checkAllowChange();
        
        this._params = KeyedList.fromList(value, io.nop.ai.agent.model.RecipeParamModel::getName);
           
    }

    
    public io.nop.ai.agent.model.RecipeParamModel getParam(String name){
        return this._params.getByKey(name);
    }

    public boolean hasParam(String name){
        return this._params.containsKey(name);
    }

    public void addParam(io.nop.ai.agent.model.RecipeParamModel item) {
        checkAllowChange();
        java.util.List<io.nop.ai.agent.model.RecipeParamModel> list = this.getParams();
        if (list == null || list.isEmpty()) {
            list = new KeyedList<>(io.nop.ai.agent.model.RecipeParamModel::getName);
            setParams(list);
        }
        list.add(item);
    }
    
    public java.util.Set<String> keySet_params(){
        return this._params.keySet();
    }

    public boolean hasParams(){
        return !this._params.isEmpty();
    }
    
    /**
     * 
     * xml name: ref
     *  
     */
    
    public java.lang.String getRef(){
      return _ref;
    }

    
    public void setRef(java.lang.String value){
        checkAllowChange();
        
        this._ref = value;
           
    }

    

    @Override
    public void freeze(boolean cascade){
        if(frozen()) return;
        super.freeze(cascade);

        if(cascade){ //NOPMD - suppressed EmptyControlStatement - Auto Gen Code
        
           this._params = io.nop.api.core.util.FreezeHelper.deepFreeze(this._params);
            
        }
    }

    @Override
    protected void outputJson(IJsonHandler out){
        super.outputJson(out);
        
        out.putNotNull("params",this.getParams());
        out.putNotNull("ref",this.getRef());
    }

    public AgentRecipeRefModel cloneInstance(){
        AgentRecipeRefModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(AgentRecipeRefModel instance){
        super.copyTo(instance);
        
        instance.setParams(this.getParams());
        instance.setRef(this.getRef());
    }

    protected AgentRecipeRefModel newInstance(){
        return (AgentRecipeRefModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
