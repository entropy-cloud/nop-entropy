package io.nop.biz.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.biz.model.BizInterceptorModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from [98:10:0:0]/nop/schema/biz/xbiz.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _BizInterceptorModel extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: forAnnotations
     * 
     */
    private java.util.Set<java.lang.String> _forAnnotations ;
    
    /**
     *  
     * xml name: forMethods
     * 
     */
    private java.util.Set<java.lang.String> _forMethods ;
    
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
    private int _order  = 100;
    
    /**
     *  
     * xml name: source
     * 
     */
    private io.nop.core.lang.eval.IEvalAction _source ;
    
    /**
     * 
     * xml name: forAnnotations
     *  
     */
    
    public java.util.Set<java.lang.String> getForAnnotations(){
      return _forAnnotations;
    }

    
    public void setForAnnotations(java.util.Set<java.lang.String> value){
        checkAllowChange();
        
        this._forAnnotations = value;
           
    }

    
    /**
     * 
     * xml name: forMethods
     *  
     */
    
    public java.util.Set<java.lang.String> getForMethods(){
      return _forMethods;
    }

    
    public void setForMethods(java.util.Set<java.lang.String> value){
        checkAllowChange();
        
        this._forMethods = value;
           
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
    
    public io.nop.core.lang.eval.IEvalAction getSource(){
      return _source;
    }

    
    public void setSource(io.nop.core.lang.eval.IEvalAction value){
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
        
        out.putNotNull("forAnnotations",this.getForAnnotations());
        out.putNotNull("forMethods",this.getForMethods());
        out.putNotNull("name",this.getName());
        out.putNotNull("order",this.getOrder());
        out.putNotNull("source",this.getSource());
    }

    public BizInterceptorModel cloneInstance(){
        BizInterceptorModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(BizInterceptorModel instance){
        super.copyTo(instance);
        
        instance.setForAnnotations(this.getForAnnotations());
        instance.setForMethods(this.getForMethods());
        instance.setName(this.getName());
        instance.setOrder(this.getOrder());
        instance.setSource(this.getSource());
    }

    protected BizInterceptorModel newInstance(){
        return (BizInterceptorModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
