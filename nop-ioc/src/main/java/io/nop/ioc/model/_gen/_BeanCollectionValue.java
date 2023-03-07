package io.nop.ioc.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from [57:6:0:0]/nop/schema/beans.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement"})
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

    

    public void freeze(boolean cascade){
        if(frozen()) return;
        super.freeze(cascade);

        if(cascade){ //NOPMD - suppressed EmptyControlStatement - Auto Gen Code
        
           this._body = io.nop.api.core.util.FreezeHelper.deepFreeze(this._body);
            
        }
    }

    protected void outputJson(IJsonHandler out){
        super.outputJson(out);
        
        out.put("body",this.getBody());
        out.put("merge",this.isMerge());
    }
}
 // resume CPD analysis - CPD-ON
