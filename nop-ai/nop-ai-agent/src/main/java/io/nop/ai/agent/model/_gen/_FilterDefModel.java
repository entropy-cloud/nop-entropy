package io.nop.ai.agent.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.ai.agent.model.FilterDefModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/ai/agent.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _FilterDefModel extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: id
     * 
     */
    private java.lang.String _id ;
    
    /**
     *  
     * xml name: impl
     * 
     */
    private java.lang.String _impl ;
    
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
     * xml name: impl
     *  
     */
    
    public java.lang.String getImpl(){
      return _impl;
    }

    
    public void setImpl(java.lang.String value){
        checkAllowChange();
        
        this._impl = value;
           
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
        
        out.putNotNull("id",this.getId());
        out.putNotNull("impl",this.getImpl());
    }

    public FilterDefModel cloneInstance(){
        FilterDefModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(FilterDefModel instance){
        super.copyTo(instance);
        
        instance.setId(this.getId());
        instance.setImpl(this.getImpl());
    }

    protected FilterDefModel newInstance(){
        return (FilterDefModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
