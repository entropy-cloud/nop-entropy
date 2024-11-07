package io.nop.batch.dsl.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.batch.dsl.model.BatchProcessorModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/task/batch.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _BatchProcessorModel extends io.nop.batch.dsl.model.BatchListenersModel {
    
    /**
     *  
     * xml name: adapter
     * 
     */
    private io.nop.core.lang.eval.IEvalFunction _adapter ;
    
    /**
     *  
     * xml name: bean
     * 
     */
    private java.lang.String _bean ;
    
    /**
     *  
     * xml name: filter
     * 
     */
    private io.nop.core.lang.eval.IEvalFunction _filter ;
    
    /**
     *  
     * xml name: name
     * 
     */
    private java.lang.String _name ;
    
    /**
     *  
     * xml name: order
     * 
     */
    private int _order  = 0;
    
    /**
     *  
     * xml name: source
     * 
     */
    private io.nop.core.lang.eval.IEvalFunction _source ;
    
    /**
     * 
     * xml name: adapter
     *  
     */
    
    public io.nop.core.lang.eval.IEvalFunction getAdapter(){
      return _adapter;
    }

    
    public void setAdapter(io.nop.core.lang.eval.IEvalFunction value){
        checkAllowChange();
        
        this._adapter = value;
           
    }

    
    /**
     * 
     * xml name: bean
     *  
     */
    
    public java.lang.String getBean(){
      return _bean;
    }

    
    public void setBean(java.lang.String value){
        checkAllowChange();
        
        this._bean = value;
           
    }

    
    /**
     * 
     * xml name: filter
     *  
     */
    
    public io.nop.core.lang.eval.IEvalFunction getFilter(){
      return _filter;
    }

    
    public void setFilter(io.nop.core.lang.eval.IEvalFunction value){
        checkAllowChange();
        
        this._filter = value;
           
    }

    
    /**
     * 
     * xml name: name
     *  
     */
    
    public java.lang.String getName(){
      return _name;
    }

    
    public void setName(java.lang.String value){
        checkAllowChange();
        
        this._name = value;
           
    }

    
    /**
     * 
     * xml name: order
     *  
     */
    
    public int getOrder(){
      return _order;
    }

    
    public void setOrder(int value){
        checkAllowChange();
        
        this._order = value;
           
    }

    
    /**
     * 
     * xml name: source
     *  
     */
    
    public io.nop.core.lang.eval.IEvalFunction getSource(){
      return _source;
    }

    
    public void setSource(io.nop.core.lang.eval.IEvalFunction value){
        checkAllowChange();
        
        this._source = value;
           
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
        
        out.putNotNull("adapter",this.getAdapter());
        out.putNotNull("bean",this.getBean());
        out.putNotNull("filter",this.getFilter());
        out.putNotNull("name",this.getName());
        out.putNotNull("order",this.getOrder());
        out.putNotNull("source",this.getSource());
    }

    public BatchProcessorModel cloneInstance(){
        BatchProcessorModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(BatchProcessorModel instance){
        super.copyTo(instance);
        
        instance.setAdapter(this.getAdapter());
        instance.setBean(this.getBean());
        instance.setFilter(this.getFilter());
        instance.setName(this.getName());
        instance.setOrder(this.getOrder());
        instance.setSource(this.getSource());
    }

    protected BatchProcessorModel newInstance(){
        return (BatchProcessorModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
