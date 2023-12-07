package io.nop.ioc.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from [147:10:0:0]/nop/schema/beans.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116"})
public abstract class _BeanOnConfigRefresh extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: invoke
     * 
     */
    private java.lang.String _invoke ;
    
    /**
     *  
     * xml name: observe
     * 
     */
    private java.util.Set<java.lang.String> _observe ;
    
    /**
     *  
     * xml name: 
     * 
     */
    private io.nop.xlang.api.EvalCode _source ;
    
    /**
     * 
     * xml name: invoke
     *  
     */
    
    public java.lang.String getInvoke(){
      return _invoke;
    }

    
    public void setInvoke(java.lang.String value){
        checkAllowChange();
        
        this._invoke = value;
           
    }

    
    /**
     * 
     * xml name: observe
     *  
     */
    
    public java.util.Set<java.lang.String> getObserve(){
      return _observe;
    }

    
    public void setObserve(java.util.Set<java.lang.String> value){
        checkAllowChange();
        
        this._observe = value;
           
    }

    
    /**
     * 
     * xml name: 
     *  
     */
    
    public io.nop.xlang.api.EvalCode getSource(){
      return _source;
    }

    
    public void setSource(io.nop.xlang.api.EvalCode value){
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
        
        out.put("invoke",this.getInvoke());
        out.put("observe",this.getObserve());
        out.put("source",this.getSource());
    }
}
 // resume CPD analysis - CPD-ON
