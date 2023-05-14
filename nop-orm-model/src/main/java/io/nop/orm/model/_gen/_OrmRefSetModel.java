package io.nop.orm.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from [142:14:0:0]/nop/schema/orm/entity.xdef <p>
 * 一对多的外键关联中父表对象可以存在集合属性来反向引用子表。集合对象可以定义keyProp，并支持排序条件。
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement"})
public abstract class _OrmRefSetModel extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: keyProp
     * 
     */
    private java.lang.String _keyProp ;
    
    /**
     *  
     * xml name: sort
     * 
     */
    private KeyedList<io.nop.api.core.beans.query.OrderFieldBean> _sort = KeyedList.emptyList();
    
    /**
     * 
     * xml name: keyProp
     *  
     */
    
    public java.lang.String getKeyProp(){
      return _keyProp;
    }

    
    public void setKeyProp(java.lang.String value){
        checkAllowChange();
        
        this._keyProp = value;
           
    }

    
    /**
     * 
     * xml name: sort
     *  
     */
    
    public java.util.List<io.nop.api.core.beans.query.OrderFieldBean> getSort(){
      return _sort;
    }

    
    public void setSort(java.util.List<io.nop.api.core.beans.query.OrderFieldBean> value){
        checkAllowChange();
        
        this._sort = KeyedList.fromList(value, io.nop.api.core.beans.query.OrderFieldBean::getName);
           
    }

    
    public io.nop.api.core.beans.query.OrderFieldBean getField(String name){
        return this._sort.getByKey(name);
    }

    public boolean hasField(String name){
        return this._sort.containsKey(name);
    }

    public void addField(io.nop.api.core.beans.query.OrderFieldBean item) {
        checkAllowChange();
        java.util.List<io.nop.api.core.beans.query.OrderFieldBean> list = this.getSort();
        if (list == null || list.isEmpty()) {
            list = new KeyedList<>(io.nop.api.core.beans.query.OrderFieldBean::getName);
            setSort(list);
        }
        list.add(item);
    }
    
    public java.util.Set<String> keySet_sort(){
        return this._sort.keySet();
    }

    public boolean hasSort(){
        return !this._sort.isEmpty();
    }
    

    public void freeze(boolean cascade){
        if(frozen()) return;
        super.freeze(cascade);

        if(cascade){ //NOPMD - suppressed EmptyControlStatement - Auto Gen Code
        
           this._sort = io.nop.api.core.util.FreezeHelper.deepFreeze(this._sort);
            
        }
    }

    protected void outputJson(IJsonHandler out){
        super.outputJson(out);
        
        out.put("keyProp",this.getKeyProp());
        out.put("sort",this.getSort());
    }
}
 // resume CPD analysis - CPD-ON
