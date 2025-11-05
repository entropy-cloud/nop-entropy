package io.nop.orm.model.interceptor._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.orm.model.interceptor.OrmInterceptorModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/orm/orm-interceptor.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _OrmInterceptorModel extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: entity
     * 
     */
    private KeyedList<io.nop.orm.model.interceptor.OrmInterceptorEntityModel> _entities = KeyedList.emptyList();
    
    /**
     * 
     * xml name: entity
     *  
     */
    
    public java.util.List<io.nop.orm.model.interceptor.OrmInterceptorEntityModel> getEntities(){
      return _entities;
    }

    
    public void setEntities(java.util.List<io.nop.orm.model.interceptor.OrmInterceptorEntityModel> value){
        checkAllowChange();
        
        this._entities = KeyedList.fromList(value, io.nop.orm.model.interceptor.OrmInterceptorEntityModel::getName);
           
    }

    
    public io.nop.orm.model.interceptor.OrmInterceptorEntityModel getEntity(String name){
        return this._entities.getByKey(name);
    }

    public boolean hasEntity(String name){
        return this._entities.containsKey(name);
    }

    public void addEntity(io.nop.orm.model.interceptor.OrmInterceptorEntityModel item) {
        checkAllowChange();
        java.util.List<io.nop.orm.model.interceptor.OrmInterceptorEntityModel> list = this.getEntities();
        if (list == null || list.isEmpty()) {
            list = new KeyedList<>(io.nop.orm.model.interceptor.OrmInterceptorEntityModel::getName);
            setEntities(list);
        }
        list.add(item);
    }
    
    public java.util.Set<String> keySet_entities(){
        return this._entities.keySet();
    }

    public boolean hasEntities(){
        return !this._entities.isEmpty();
    }
    

    @Override
    public void freeze(boolean cascade){
        if(frozen()) return;
        super.freeze(cascade);

        if(cascade){ //NOPMD - suppressed EmptyControlStatement - Auto Gen Code
        
           this._entities = io.nop.api.core.util.FreezeHelper.deepFreeze(this._entities);
            
        }
    }

    @Override
    protected void outputJson(IJsonHandler out){
        super.outputJson(out);
        
        out.putNotNull("entities",this.getEntities());
    }

    public OrmInterceptorModel cloneInstance(){
        OrmInterceptorModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(OrmInterceptorModel instance){
        super.copyTo(instance);
        
        instance.setEntities(this.getEntities());
    }

    protected OrmInterceptorModel newInstance(){
        return (OrmInterceptorModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
