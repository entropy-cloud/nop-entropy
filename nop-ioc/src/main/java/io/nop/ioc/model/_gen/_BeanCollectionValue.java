package io.nop.ioc.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.ioc.model.BeanCollectionValue;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/beans.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _BeanCollectionValue extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: 
     * 
     */
    private java.util.List<io.nop.ioc.model.IBeanPropValue> _body = java.util.Collections.emptyList();
    
    /**
     *  
     * xml name: merge
     * 
     */
    private boolean _merge  = false;
    
    /**
     * 
     * xml name: 
     *  
     */
    
    public java.util.List<io.nop.ioc.model.IBeanPropValue> getBody(){
      return _body;
    }

    
    public void setBody(java.util.List<io.nop.ioc.model.IBeanPropValue> value){
        checkAllowChange();
        
        this._body = value;
           
    }

    
    /**
     * 
     * xml name: merge
     *  
     */
    
    public boolean isMerge(){
      return _merge;
    }

    
    public void setMerge(boolean value){
        checkAllowChange();
        
        this._merge = value;
           
    }

    

    @Override
    public void freeze(boolean cascade){
        if(frozen()) return;
        super.freeze(cascade);

        if(cascade){ //NOPMD - suppressed EmptyControlStatement - Auto Gen Code
        
           this._body = io.nop.api.core.util.FreezeHelper.deepFreeze(this._body);
            
        }
    }

    @Override
    protected void outputJson(IJsonHandler out){
        super.outputJson(out);
        
        out.putNotNull("body",this.getBody());
        out.putNotNull("merge",this.isMerge());
    }

    public BeanCollectionValue cloneInstance(){
        BeanCollectionValue instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(BeanCollectionValue instance){
        super.copyTo(instance);
        
        instance.setBody(this.getBody());
        instance.setMerge(this.isMerge());
    }

    protected BeanCollectionValue newInstance(){
        return (BeanCollectionValue) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
