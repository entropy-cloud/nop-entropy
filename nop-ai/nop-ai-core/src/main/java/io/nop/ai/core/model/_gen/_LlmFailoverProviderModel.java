package io.nop.ai.core.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.ai.core.model.LlmFailoverProviderModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/ai/llm-failover.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _LlmFailoverProviderModel extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: model
     * 
     */
    private java.lang.String _model ;
    
    /**
     *  
     * xml name: provider
     * 
     */
    private java.lang.String _provider ;
    
    /**
     *  
     * xml name: tier
     * 
     */
    private java.lang.String _tier ;
    
    /**
     * 
     * xml name: model
     *  
     */
    
    public java.lang.String getModel(){
      return _model;
    }

    
    public void setModel(java.lang.String value){
        checkAllowChange();
        
        this._model = value;
           
    }

    
    /**
     * 
     * xml name: provider
     *  
     */
    
    public java.lang.String getProvider(){
      return _provider;
    }

    
    public void setProvider(java.lang.String value){
        checkAllowChange();
        
        this._provider = value;
           
    }

    
    /**
     * 
     * xml name: tier
     *  
     */
    
    public java.lang.String getTier(){
      return _tier;
    }

    
    public void setTier(java.lang.String value){
        checkAllowChange();
        
        this._tier = value;
           
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
        
        out.putNotNull("model",this.getModel());
        out.putNotNull("provider",this.getProvider());
        out.putNotNull("tier",this.getTier());
    }

    public LlmFailoverProviderModel cloneInstance(){
        LlmFailoverProviderModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(LlmFailoverProviderModel instance){
        super.copyTo(instance);
        
        instance.setModel(this.getModel());
        instance.setProvider(this.getProvider());
        instance.setTier(this.getTier());
    }

    protected LlmFailoverProviderModel newInstance(){
        return (LlmFailoverProviderModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
