package io.nop.ioc.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.ioc.model.BeanListenerModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from [209:6:0:0]/nop/schema/beans.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _BeanListenerModel extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: events
     * 
     */
    private java.util.Set<java.lang.String> _events ;
    
    /**
     *  
     * xml name: id
     * 
     */
    private java.lang.String _id ;
    
    /**
     *  
     * xml name: ioc:allow-override
     * 
     */
    private boolean _iocAllowOverride  = false;
    
    /**
     *  
     * xml name: ioc:condition
     * 满足条件时bean才允许实例化
     */
    private io.nop.ioc.model.BeanConditionModel _iocCondition ;
    
    /**
     *  
     * xml name: ref
     * 引用bean的定义
     */
    private java.lang.String _ref ;
    
    /**
     *  
     * xml name: source
     * 
     */
    private io.nop.xlang.api.EvalCode _source ;
    
    /**
     * 
     * xml name: events
     *  
     */
    
    public java.util.Set<java.lang.String> getEvents(){
      return _events;
    }

    
    public void setEvents(java.util.Set<java.lang.String> value){
        checkAllowChange();
        
        this._events = value;
           
    }

    
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
     * xml name: ioc:allow-override
     *  
     */
    
    public boolean isIocAllowOverride(){
      return _iocAllowOverride;
    }

    
    public void setIocAllowOverride(boolean value){
        checkAllowChange();
        
        this._iocAllowOverride = value;
           
    }

    
    /**
     * 
     * xml name: ioc:condition
     *  满足条件时bean才允许实例化
     */
    
    public io.nop.ioc.model.BeanConditionModel getIocCondition(){
      return _iocCondition;
    }

    
    public void setIocCondition(io.nop.ioc.model.BeanConditionModel value){
        checkAllowChange();
        
        this._iocCondition = value;
           
    }

    
    /**
     * 
     * xml name: ref
     *  引用bean的定义
     */
    
    public java.lang.String getRef(){
      return _ref;
    }

    
    public void setRef(java.lang.String value){
        checkAllowChange();
        
        this._ref = value;
           
    }

    
    /**
     * 
     * xml name: source
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
        
           this._iocCondition = io.nop.api.core.util.FreezeHelper.deepFreeze(this._iocCondition);
            
        }
    }

    @Override
    protected void outputJson(IJsonHandler out){
        super.outputJson(out);
        
        out.put("events",this.getEvents());
        out.put("id",this.getId());
        out.put("iocAllowOverride",this.isIocAllowOverride());
        out.put("iocCondition",this.getIocCondition());
        out.put("ref",this.getRef());
        out.put("source",this.getSource());
    }

    public BeanListenerModel cloneInstance(){
        BeanListenerModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(BeanListenerModel instance){
        super.copyTo(instance);
        
        instance.setEvents(this.getEvents());
        instance.setId(this.getId());
        instance.setIocAllowOverride(this.isIocAllowOverride());
        instance.setIocCondition(this.getIocCondition());
        instance.setRef(this.getRef());
        instance.setSource(this.getSource());
    }

    protected BeanListenerModel newInstance(){
        return (BeanListenerModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
