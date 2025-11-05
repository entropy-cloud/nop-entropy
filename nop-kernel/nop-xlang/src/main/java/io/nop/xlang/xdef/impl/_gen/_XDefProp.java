package io.nop.xlang.xdef.impl._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.xlang.xdef.impl.XDefProp;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/xdef.xdef <p>
 * xdef转换为objMeta时，objPropMeta上存在的扩展属性
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _XDefProp extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: name
     * 
     */
    private java.lang.String _name ;
    
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
        
        out.putNotNull("name",this.getName());
    }

    public XDefProp cloneInstance(){
        XDefProp instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(XDefProp instance){
        super.copyTo(instance);
        
        instance.setName(this.getName());
    }

    protected XDefProp newInstance(){
        return (XDefProp) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
