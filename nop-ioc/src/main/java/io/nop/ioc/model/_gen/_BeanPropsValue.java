package io.nop.ioc.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from [95:6:0:0]/nop/schema/beans.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement"})
public abstract class _BeanPropsValue extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: 
     * 
     */
    private KeyedList<io.nop.ioc.model.BeanPropEntryValue> _body = KeyedList.emptyList();
    
    /**
     *  
     * xml name: ioc:location
     * 
     */
    private java.lang.String _iocLocation ;
    
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
    
    public java.util.List<io.nop.ioc.model.BeanPropEntryValue> getBody(){
      return _body;
    }

    
    public void setBody(java.util.List<io.nop.ioc.model.BeanPropEntryValue> value){
        checkAllowChange();
        
        this._body = KeyedList.fromList(value, io.nop.ioc.model.BeanPropEntryValue::getKey);
           
    }

    
    public io.nop.ioc.model.BeanPropEntryValue getProp(String name){
        return this._body.getByKey(name);
    }

    public boolean hasProp(String name){
        return this._body.containsKey(name);
    }

    public void addProp(io.nop.ioc.model.BeanPropEntryValue item) {
        checkAllowChange();
        java.util.List<io.nop.ioc.model.BeanPropEntryValue> list = this.getBody();
        if (list == null || list.isEmpty()) {
            list = new KeyedList<>(io.nop.ioc.model.BeanPropEntryValue::getKey);
            setBody(list);
        }
        list.add(item);
    }
    
    public java.util.Set<String> keySet_body(){
        return this._body.keySet();
    }

    public boolean hasBody(){
        return !this._body.isEmpty();
    }
    
    /**
     * 
     * xml name: ioc:location
     *  
     */
    
    public java.lang.String getIocLocation(){
      return _iocLocation;
    }

    
    public void setIocLocation(java.lang.String value){
        checkAllowChange();
        
        this._iocLocation = value;
           
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
        out.put("iocLocation",this.getIocLocation());
        out.put("merge",this.isMerge());
    }
}
 // resume CPD analysis - CPD-ON
