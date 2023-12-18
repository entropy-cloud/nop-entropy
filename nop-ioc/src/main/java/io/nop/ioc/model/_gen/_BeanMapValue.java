package io.nop.ioc.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from [88:6:0:0]/nop/schema/beans.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _BeanMapValue extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: 
     * 
     */
    private KeyedList<io.nop.ioc.model.BeanEntryValue> _body = KeyedList.emptyList();
    
    /**
     *  
     * xml name: key-type
     * 
     */
    private java.lang.String _keyType ;
    
    /**
     *  
     * xml name: map-class
     * 
     */
    private java.lang.String _mapClass ;
    
    /**
     *  
     * xml name: merge
     * 
     */
    private boolean _merge  = false;
    
    /**
     *  
     * xml name: value-type
     * 
     */
    private java.lang.String _valueType ;
    
    /**
     * 
     * xml name: 
     *  
     */
    
    public java.util.List<io.nop.ioc.model.BeanEntryValue> getBody(){
      return _body;
    }

    
    public void setBody(java.util.List<io.nop.ioc.model.BeanEntryValue> value){
        checkAllowChange();
        
        this._body = KeyedList.fromList(value, io.nop.ioc.model.BeanEntryValue::getKey);
           
    }

    
    public io.nop.ioc.model.BeanEntryValue getEntry(String name){
        return this._body.getByKey(name);
    }

    public boolean hasEntry(String name){
        return this._body.containsKey(name);
    }

    public void addEntry(io.nop.ioc.model.BeanEntryValue item) {
        checkAllowChange();
        java.util.List<io.nop.ioc.model.BeanEntryValue> list = this.getBody();
        if (list == null || list.isEmpty()) {
            list = new KeyedList<>(io.nop.ioc.model.BeanEntryValue::getKey);
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
     * xml name: key-type
     *  
     */
    
    public java.lang.String getKeyType(){
      return _keyType;
    }

    
    public void setKeyType(java.lang.String value){
        checkAllowChange();
        
        this._keyType = value;
           
    }

    
    /**
     * 
     * xml name: map-class
     *  
     */
    
    public java.lang.String getMapClass(){
      return _mapClass;
    }

    
    public void setMapClass(java.lang.String value){
        checkAllowChange();
        
        this._mapClass = value;
           
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

    
    /**
     * 
     * xml name: value-type
     *  
     */
    
    public java.lang.String getValueType(){
      return _valueType;
    }

    
    public void setValueType(java.lang.String value){
        checkAllowChange();
        
        this._valueType = value;
           
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
        
        out.put("body",this.getBody());
        out.put("keyType",this.getKeyType());
        out.put("mapClass",this.getMapClass());
        out.put("merge",this.isMerge());
        out.put("valueType",this.getValueType());
    }
}
 // resume CPD analysis - CPD-ON
