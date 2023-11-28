package io.nop.ioc.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from [245:6:0:0]/nop/schema/beans.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement"})
public abstract class _BeanAliasModel extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: alias
     * 逗号分隔的新的bean的别名
     */
    private java.util.Set<java.lang.String> _alias ;
    
    /**
     *  
     * xml name: name
     * 已定义的bean的name或者id
     */
    private java.lang.String _name ;
    
    /**
     * 
     * xml name: alias
     *  逗号分隔的新的bean的别名
     */
    
    public java.util.Set<java.lang.String> getAlias(){
      return _alias;
    }

    
    public void setAlias(java.util.Set<java.lang.String> value){
        checkAllowChange();
        
        this._alias = value;
           
    }

    
    /**
     * 
     * xml name: name
     *  已定义的bean的name或者id
     */
    
    public java.lang.String getName(){
      return _name;
    }

    
    public void setName(java.lang.String value){
        checkAllowChange();
        
        this._name = value;
           
    }

    

    public void freeze(boolean cascade){
        if(frozen()) return;
        super.freeze(cascade);

        if(cascade){ //NOPMD - suppressed EmptyControlStatement - Auto Gen Code
        
        }
    }

    protected void outputJson(IJsonHandler out){
        super.outputJson(out);
        
        out.put("alias",this.getAlias());
        out.put("name",this.getName());
    }
}
 // resume CPD analysis - CPD-ON
