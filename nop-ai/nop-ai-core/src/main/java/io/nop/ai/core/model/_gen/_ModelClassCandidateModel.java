package io.nop.ai.core.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.ai.core.model.ModelClassCandidateModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/ai/model-class.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _ModelClassCandidateModel extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: accountRef
     * 
     */
    private java.lang.String _accountRef ;
    
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
     * xml name: accountRef
     *  
     */
    
    public java.lang.String getAccountRef(){
      return _accountRef;
    }

    
    public void setAccountRef(java.lang.String value){
        checkAllowChange();
        
        this._accountRef = value;
           
    }

    
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
        
        out.putNotNull("accountRef",this.getAccountRef());
        out.putNotNull("model",this.getModel());
        out.putNotNull("provider",this.getProvider());
    }

    public ModelClassCandidateModel cloneInstance(){
        ModelClassCandidateModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(ModelClassCandidateModel instance){
        super.copyTo(instance);
        
        instance.setAccountRef(this.getAccountRef());
        instance.setModel(this.getModel());
        instance.setProvider(this.getProvider());
    }

    protected ModelClassCandidateModel newInstance(){
        return (ModelClassCandidateModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
