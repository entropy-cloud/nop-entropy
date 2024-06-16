package io.nop.orm.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.orm.model.OrmToManyReferenceModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/orm/entity.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _OrmToManyReferenceModel extends io.nop.orm.model.OrmReferenceModel {
    
    /**
     *  
     * xml name: keyProp
     * 如果指定keyProp，则一对多集合中的元素以keyProp为唯一键，因此可以通过属性语法访问。
     * 例如children集合的keyProp为id, 则children.myElm表示访问children集合中id=myElm的元素。如果child实体设置了kvTable=true，
     * 则children.myElm实际返回的是children.findById("myElm").getFieldValue()
     */
    private java.lang.String _keyProp ;
    
    /**
     *  
     * xml name: maxSize
     * 如果大于0，则限制集合中的元素个数不能超过maxSize，否则会抛出异常
     */
    private java.lang.Integer _maxSize ;
    
    /**
     *  
     * xml name: sort
     * 
     */
    private KeyedList<io.nop.api.core.beans.query.OrderFieldBean> _sort = KeyedList.emptyList();
    
    /**
     *  
     * xml name: useGlobalCache
     * 
     */
    private boolean _useGlobalCache  = false;
    
    /**
     * 
     * xml name: keyProp
     *  如果指定keyProp，则一对多集合中的元素以keyProp为唯一键，因此可以通过属性语法访问。
     * 例如children集合的keyProp为id, 则children.myElm表示访问children集合中id=myElm的元素。如果child实体设置了kvTable=true，
     * 则children.myElm实际返回的是children.findById("myElm").getFieldValue()
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
     * xml name: maxSize
     *  如果大于0，则限制集合中的元素个数不能超过maxSize，否则会抛出异常
     */
    
    public java.lang.Integer getMaxSize(){
      return _maxSize;
    }

    
    public void setMaxSize(java.lang.Integer value){
        checkAllowChange();
        
        this._maxSize = value;
           
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
    
    /**
     * 
     * xml name: useGlobalCache
     *  
     */
    
    public boolean isUseGlobalCache(){
      return _useGlobalCache;
    }

    
    public void setUseGlobalCache(boolean value){
        checkAllowChange();
        
        this._useGlobalCache = value;
           
    }

    

    @Override
    public void freeze(boolean cascade){
        if(frozen()) return;
        super.freeze(cascade);

        if(cascade){ //NOPMD - suppressed EmptyControlStatement - Auto Gen Code
        
           this._sort = io.nop.api.core.util.FreezeHelper.deepFreeze(this._sort);
            
        }
    }

    @Override
    protected void outputJson(IJsonHandler out){
        super.outputJson(out);
        
        out.putNotNull("keyProp",this.getKeyProp());
        out.putNotNull("maxSize",this.getMaxSize());
        out.putNotNull("sort",this.getSort());
        out.putNotNull("useGlobalCache",this.isUseGlobalCache());
    }

    public OrmToManyReferenceModel cloneInstance(){
        OrmToManyReferenceModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(OrmToManyReferenceModel instance){
        super.copyTo(instance);
        
        instance.setKeyProp(this.getKeyProp());
        instance.setMaxSize(this.getMaxSize());
        instance.setSort(this.getSort());
        instance.setUseGlobalCache(this.isUseGlobalCache());
    }

    protected OrmToManyReferenceModel newInstance(){
        return (OrmToManyReferenceModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
