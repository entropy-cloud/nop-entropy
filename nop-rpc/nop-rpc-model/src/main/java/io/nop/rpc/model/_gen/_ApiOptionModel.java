package io.nop.rpc.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.rpc.model.ApiOptionModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from [11:10:0:0]/nop/schema/api.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _ApiOptionModel extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: name
     * 
     */
    private java.lang.String _name ;
    
    /**
     *  
     * xml name: value
     * 
     */
    private java.lang.String _value ;
    
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
     * xml name: value
     *  
     */
    
    public java.lang.String getValue(){
      return _value;
    }

    
    public void setValue(java.lang.String value){
        checkAllowChange();
        
        this._value = value;
           
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
        
        out.put("name",this.getName());
        out.put("value",this.getValue());
    }

    public ApiOptionModel cloneInstance(){
        ApiOptionModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(ApiOptionModel instance){
        super.copyTo(instance);
        
        instance.setName(this.getName());
        instance.setValue(this.getValue());
    }

    protected ApiOptionModel newInstance(){
        return (ApiOptionModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
