package io.nop.ioc.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.ioc.model.BeanPropValue;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from [15:6:0:0]/nop/schema/beans.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _BeanPropValue extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: 
     * 
     */
    private io.nop.ioc.model.IBeanPropValue _body ;
    
    /**
     * 
     * xml name: 
     *  
     */
    
    public io.nop.ioc.model.IBeanPropValue getBody(){
      return _body;
    }

    
    public void setBody(io.nop.ioc.model.IBeanPropValue value){
        checkAllowChange();
        
        this._body = value;
           
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
    }

    public BeanPropValue cloneInstance(){
        BeanPropValue instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(BeanPropValue instance){
        super.copyTo(instance);
        
        instance.setBody(this.getBody());
    }

    protected BeanPropValue newInstance(){
        return (BeanPropValue) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
